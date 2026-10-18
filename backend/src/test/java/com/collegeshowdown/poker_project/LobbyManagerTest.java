package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.models.College.College;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.DealerService;
import com.collegeshowdown.poker_project.services.LeaderboardService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.LobbyManagerService;
import com.collegeshowdown.poker_project.services.LobbyManagerService.LobbyAssignment;
import com.collegeshowdown.poker_project.services.LobbyManagerService.SeatingException;
import com.collegeshowdown.poker_project.services.PotManagerService;
import com.collegeshowdown.poker_project.services.WinnerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bank ↔ table chip accounting, rebuy rules and seating. The repository is a
 * mock backed by an in-memory "database row" per player, so these tests check
 * what would actually be persisted — not the in-memory PlayerRecord a seat
 * holds, which is a stale snapshot by design.
 */
class LobbyManagerTest {

    /** Simulated DB rows: id → {bank, totalProfit, monthlyProfit}. */
    private final Map<Integer, int[]> db = new HashMap<>();
    private PlayerRepository repo;
    private LobbyManagerService manager;

    @BeforeEach
    void setUp() {
        repo = mock(PlayerRepository.class);
        when(repo.withdrawFromBank(anyInt(), anyInt())).thenAnswer(inv -> {
            int[] row = db.get(inv.<Integer>getArgument(0));
            int amount = inv.getArgument(1);
            if (row == null || row[0] < amount) return 0;
            row[0] -= amount;
            return 1;
        });
        when(repo.depositToBank(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int[] row = db.get(inv.<Integer>getArgument(0));
            row[0] += inv.<Integer>getArgument(1);
            row[1] += inv.<Integer>getArgument(2);
            row[2] += inv.<Integer>getArgument(2);
            return 1;
        });

        LobbyGameService game = new LobbyGameService(
                mock(DealerService.class),
                mock(PotManagerService.class),
                mock(WinnerService.class),
                mock(GameStateBuilder.class),
                mock(SimpMessagingTemplate.class),
                null);
        manager = new LobbyManagerService(game, repo);
    }

    /** A player whose DB row holds {@code bank} chips; the returned record mirrors it (as a fresh load would). */
    private PlayerRecord player(int id, int bank) {
        PlayerRecord record = new PlayerRecord("p" + id, "p" + id + "@umass.edu",
                "x", "umass.edu", "UMass", "GOOGLE");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        record.setTotalChips(bank);
        db.put(id, new int[] { bank, 0, 0 });
        return record;
    }

    private int bank(int id) { return db.get(id)[0]; }
    private int profit(int id) { return db.get(id)[1]; }

    private ConnectedPlayer seated(LobbyAssignment a) {
        return a.lobby().getPlayersAtTable()[a.seat()];
    }

    // ─── Chip conservation / profit ──────────────────────────────────────────────

    @Test
    void buyInRebuyAndCashOutConserveChips() {
        PlayerRecord rec = player(1, 1000);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 100);
        assertEquals(900, bank(1));

        // Lose the stack, then rebuy — previously the rebuy's deduction was
        // written via a different PlayerRecord copy and then overwritten by
        // the seat's stale copy at cash-out, minting the rebuy for free.
        seated(a).buyIn(0);
        assertEquals(150, manager.rebuy(a.lobby(), 1, 150));
        assertEquals(750, bank(1));

        manager.leaveTable(a.lobby(), a.seat());
        assertEquals(900, bank(1), "cash-out returns exactly the 150 left on the table");
        assertEquals(-100, profit(1), "lost 100 + rebought 150, left with 150");
    }

    @Test
    void sittingDownIsNotRecordedAsALoss() {
        PlayerRecord rec = player(1, 1000);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 200);
        assertEquals(0, profit(1), "profit is settled at cash-out, not deducted at buy-in");

        seated(a).buyIn(260); // won 60
        manager.leaveTable(a.lobby(), a.seat());
        assertEquals(1060, bank(1));
        assertEquals(60, profit(1));
    }

    @Test
    void leaveTwiceOnlyCashesOutOnce() {
        PlayerRecord rec = player(1, 1000);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 200);
        manager.leaveTable(a.lobby(), a.seat());
        manager.leaveTable(a.lobby(), a.seat());
        assertEquals(1000, bank(1));
        verify(repo, times(1)).depositToBank(anyInt(), anyInt(), anyInt());
    }

    @Test
    void failedCashOutKeepsPlayerSeatedAndRetryCreditsOnce() {
        PlayerRecord rec = player(1, 1000);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 200);

        doThrow(new DataAccessResourceFailureException("db down"))
                .when(repo).depositToBank(anyInt(), anyInt(), anyInt());
        assertDoesNotThrow(() -> manager.leaveTable(a.lobby(), a.seat()));
        assertNotNull(a.lobby().getPlayersAtTable()[a.seat()], "seat (and its chips) kept on DB failure");
        assertEquals(800, bank(1));

        // DB is back.
        doAnswer(inv -> {
            db.get(inv.<Integer>getArgument(0))[0] += inv.<Integer>getArgument(1);
            db.get(inv.<Integer>getArgument(0))[1] += inv.<Integer>getArgument(2);
            return 1;
        }).when(repo).depositToBank(anyInt(), anyInt(), anyInt());
        manager.leaveTable(a.lobby(), a.seat());
        assertNull(a.lobby().getPlayersAtTable()[a.seat()]);
        assertEquals(1000, bank(1));
        assertEquals(0, profit(1));
    }

    @Test
    void shutdownReturnsStacksAndUnfinishedHandContributions() {
        PlayerRecord r1 = player(1, 1000);
        PlayerRecord r2 = player(2, 1000);
        LobbyAssignment a1 = manager.joinLobby(r1, StakesLevel.LOW, false, 200);
        LobbyAssignment a2 = manager.joinLobby(r2, StakesLevel.LOW, false, 200);
        Lobby lobby = a1.lobby();
        assertNotEquals(GamePhase.WAITING, lobby.getGamePhase());

        // Mid-hand: player 1 has 30 in the pot.
        seated(a1).buyIn(170);
        lobby.addHandContribution(seated(a1), 30);

        manager.cashOutAllOnShutdown();
        assertEquals(1000, bank(1));
        assertEquals(1000, bank(2));
        assertEquals(0, profit(1));
        assertNull(lobby.getPlayersAtTable()[a2.seat()]);
    }

    @Test
    void shutdownReturnsPotChipsOfAPlayerWhoAlreadyLeftTheHand() {
        LobbyAssignment a1 = manager.joinLobby(player(1, 1000), StakesLevel.LOW, false, 200);
        manager.joinLobby(player(2, 1000), StakesLevel.LOW, false, 200);
        LobbyAssignment a3 = manager.joinLobby(player(3, 1000), StakesLevel.LOW, false, 200);
        Lobby lobby = a1.lobby();
        assertNotEquals(GamePhase.WAITING, lobby.getGamePhase());

        // Player 3 put 30 in the pot, folded and left: cashed out 170.
        seated(a3).buyIn(170);
        lobby.addHandContribution(seated(a3), 30);
        manager.leaveTable(lobby, a3.seat());
        assertEquals(970, bank(3));
        assertEquals(-30, profit(3));

        // The hand never finishes — its pot is unwound, not destroyed.
        manager.cashOutAllOnShutdown();
        assertEquals(1000, bank(3), "the 30 in the unfinished pot must come back");
        assertEquals(0, profit(3));
        assertEquals(1000, bank(1));
        assertEquals(1000, bank(2));
    }

    @Test
    void shutdownDoesNotDoubleRefundAPlayerWhoLeftAndSatBackDown() {
        LobbyAssignment a1 = manager.joinLobby(player(1, 1000), StakesLevel.LOW, false, 200);
        manager.joinLobby(player(2, 1000), StakesLevel.LOW, false, 200);
        LobbyAssignment a3 = manager.joinLobby(player(3, 1000), StakesLevel.LOW, false, 200);
        Lobby lobby = a1.lobby();

        seated(a3).buyIn(170);
        lobby.addHandContribution(seated(a3), 30);
        manager.leaveTable(lobby, a3.seat());
        LobbyAssignment back = manager.joinLobby(rec(3), StakesLevel.LOW, false, 200);
        assertSame(lobby, back.lobby());
        assertEquals(770, bank(3));

        manager.cashOutAllOnShutdown();
        assertEquals(1000, bank(3));
        assertEquals(0, profit(3));
    }

    // ─── Rebuy rules ─────────────────────────────────────────────────────────────

    @Test
    void rebuyIsCappedAtTableMaximum() {
        PlayerRecord rec = player(1, 1000);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 150);
        // LOW max buy-in = 100 big blinds = 200
        assertEquals(50, manager.rebuy(a.lobby(), 1, 500));
        assertEquals(200, seated(a).getActiveChips());
        assertEquals(800, bank(1));
        assertThrows(SeatingException.class, () -> manager.rebuy(a.lobby(), 1, 10));
        assertEquals(800, bank(1));
    }

    @Test
    void rebuyIntoALiveHandIsRejected() {
        player(1, 1000);
        player(2, 1000);
        LobbyAssignment a1 = manager.joinLobby(rec(1), StakesLevel.LOW, false, 100);
        manager.joinLobby(rec(2), StakesLevel.LOW, false, 100);
        assertEquals(GamePhase.PRE_FLOP, a1.lobby().getGamePhase());

        // Dealt in and still playing this hand.
        seated(a1).addCards(List.of(new Card(Rank.ACE, Suit.SPADES), new Card(Rank.KING, Suit.SPADES)));
        assertThrows(SeatingException.class, () -> manager.rebuy(a1.lobby(), 1, 50));
        assertEquals(100, seated(a1).getActiveChips());
        assertEquals(900, bank(1));
    }

    @Test
    void allInPlayerCannotRebuyBackIntoTheRunningHand() {
        player(1, 1000);
        player(2, 1000);
        LobbyAssignment a1 = manager.joinLobby(rec(1), StakesLevel.LOW, false, 100);
        manager.joinLobby(rec(2), StakesLevel.LOW, false, 100);

        // All-in for their whole stack this hand (no cards, as when all-in on a blind).
        ConnectedPlayer p = seated(a1);
        p.buyIn(0);
        p.setPlayerState(PlayerState.ALL_IN);
        p.getCards().clear();
        a1.lobby().addHandContribution(p, 100);

        assertThrows(SeatingException.class, () -> manager.rebuy(a1.lobby(), 1, 100));
        assertEquals(PlayerState.ALL_IN, p.getPlayerState());
        assertEquals(900, bank(1));
    }

    @Test
    void bustedPlayerRebuyingMidHandSitsOutUntilNextHand() {
        player(1, 1000);
        player(2, 1000);
        player(3, 1000);
        LobbyAssignment a1 = manager.joinLobby(rec(1), StakesLevel.LOW, false, 100);
        manager.joinLobby(rec(2), StakesLevel.LOW, false, 100);
        manager.joinLobby(rec(3), StakesLevel.LOW, false, 100);
        assertEquals(GamePhase.PRE_FLOP, a1.lobby().getGamePhase());

        // Busted last hand: 0 chips, still ALL_IN, not dealt into this one.
        ConnectedPlayer busted = seated(a1);
        busted.buyIn(0);
        busted.setPlayerState(PlayerState.ALL_IN);
        busted.getCards().clear();

        assertEquals(100, manager.rebuy(a1.lobby(), 1, 100));
        assertEquals(100, busted.getActiveChips());
        assertNotEquals(PlayerState.ACTIVE, busted.getPlayerState(),
                "must not be handed turns in a hand they were never dealt into");
    }

    @Test
    void rebuyBeyondBankIsRejected() {
        PlayerRecord rec = player(1, 250);
        LobbyAssignment a = manager.joinLobby(rec, StakesLevel.LOW, false, 200);
        seated(a).buyIn(0);
        assertThrows(SeatingException.class, () -> manager.rebuy(a.lobby(), 1, 100));
        assertEquals(50, bank(1));
        assertEquals(0, seated(a).getActiveChips());
    }

    // ─── Joining ─────────────────────────────────────────────────────────────────

    @Test
    void joinBelowMinimumIsRejectedWithoutTouchingBank() {
        PlayerRecord rec = player(1, 30); // LOW minimum is 40
        assertThrows(SeatingException.class, () -> manager.joinLobby(rec, StakesLevel.LOW, false, 0));
        PlayerRecord rec2 = player(2, 1000);
        assertThrows(SeatingException.class, () -> manager.joinLobby(rec2, StakesLevel.LOW, false, 10));
        verify(repo, never()).withdrawFromBank(anyInt(), anyInt());
    }

    @Test
    void seatedPlayerWithEmptyBankCanStillReconnect() {
        PlayerRecord rec = player(1, 200);
        LobbyAssignment first = manager.joinLobby(rec, StakesLevel.LOW, false, 200);
        assertEquals(0, bank(1));

        PlayerRecord reloaded = rec(1); // fresh load: bank 0
        LobbyAssignment again = assertDoesNotThrow(
                () -> manager.joinLobby(reloaded, StakesLevel.LOW, false, 0));
        assertSame(first.lobby(), again.lobby());
        assertEquals(first.seat(), again.seat());
        assertEquals(200, again.buyIn());
    }

    @Test
    void reconnectCancelsPendingDeparture() {
        LobbyAssignment a = manager.joinLobby(player(1, 1000), StakesLevel.LOW, false, 100);
        a.lobby().getPendingDepartureSeats().add(a.seat());
        manager.joinLobby(rec(1), StakesLevel.LOW, false, 0);
        assertFalse(a.lobby().getPendingDepartureSeats().contains(a.seat()));
    }

    @Test
    void emptiedLobbyIsRetiredAndNotReused() {
        LobbyAssignment a = manager.joinLobby(player(1, 1000), StakesLevel.LOW, false, 100);
        manager.leaveTable(a.lobby(), a.seat());
        assertNull(manager.getLobbyById(a.lobby().getId()));

        LobbyAssignment b = manager.joinLobby(player(2, 1000), StakesLevel.LOW, false, 100);
        assertNotSame(a.lobby(), b.lobby());
        assertSame(b.lobby(), manager.getLobbyById(b.lobby().getId()));
    }

    /** A fresh "load" of an existing simulated row. */
    private PlayerRecord rec(int id) {
        int bank = db.get(id)[0];
        PlayerRecord record = new PlayerRecord("p" + id, "p" + id + "@umass.edu",
                "x", "umass.edu", "UMass", "GOOGLE");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        record.setTotalChips(bank);
        return record;
    }

    // ─── Seating failure refund ──────────────────────────────────────────────────

    @Test
    void buyInIsRefundedWhenSeatingFailsBeforeTheSeatIsWritten() {
        player(1, 1000);
        PlayerRecord broken = mock(PlayerRecord.class);
        when(broken.getId()).thenReturn(1);
        when(broken.getTotalChips()).thenReturn(1000);
        when(broken.getCollegeDomain()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class,
                () -> manager.joinLobby(broken, StakesLevel.LOW, true, 100));
        assertEquals(1000, bank(1), "withdrawn buy-in must come back");
        assertEquals(0, profit(1));
        assertEquals(0, manager.getSessionBuyIn(1));
    }

    // ─── Leaderboards ────────────────────────────────────────────────────────────

    private static PlayerRecord leaderboardRow(int id, String domain) {
        PlayerRecord record = new PlayerRecord("p" + id, "p" + id + "@" + domain,
                "x", domain, domain.toUpperCase(), "GOOGLE");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return record;
    }

    @Test
    void schoolLeaderboardRanksAggregatedTotalsAndFindsMySchool() {
        when(repo.sumAllTimeProfitBySchool()).thenReturn(List.of(
                new Object[] { "umass.edu", "UMass", 50L },
                new Object[] { "mit.edu", "MIT", 300L },
                new Object[] { "bu.edu", "BU", 50L },
                new Object[] { "yale.edu", "Yale", null }));
        LeaderboardService lb = new LeaderboardService(repo);

        var result = lb.schoolLeaderboard("umass.edu", LeaderboardService.Range.ALL_TIME);
        assertEquals(List.of("mit.edu", "bu.edu", "umass.edu", "yale.edu"),
                result.top10().stream().map(LeaderboardService.SchoolEntry::collegeDomain).toList());
        assertEquals(3, result.mySchool().rank());
        assertEquals(0, result.top10().get(3).profit());
        verify(repo, never()).findAll();
    }

    @Test
    void playerRankOutsideTopTenComesFromACountNotATableScan() {
        PlayerRecord me = leaderboardRow(42, "umass.edu");
        when(repo.findTopPlayersByMonthlyProfit(any())).thenReturn(List.of(leaderboardRow(1, "mit.edu")));
        when(repo.findById(42)).thenReturn(Optional.of(me));
        when(repo.countAheadByMonthlyProfit(0, 42)).thenReturn(57L);
        LeaderboardService lb = new LeaderboardService(repo);

        var result = lb.playerLeaderboard(42, LeaderboardService.Range.MONTHLY);
        assertEquals(58, result.me().rank());
        assertEquals("p42", result.me().name());
        verify(repo, never()).findAll(any(org.springframework.data.domain.Sort.class));
    }

    @Test
    void schoolPlayerRankOutsideTopTenIsCountedWithinTheSchool() {
        PlayerRecord me = leaderboardRow(42, "umass.edu");
        when(repo.findByCollegeDomainOrderedByAllTimeProfit(eq("umass.edu"), any())).thenReturn(List.of());
        when(repo.findById(42)).thenReturn(Optional.of(me));
        when(repo.countAheadInSchoolByAllTimeProfit("umass.edu", 0, 42)).thenReturn(10L);
        LeaderboardService lb = new LeaderboardService(repo);

        var result = lb.schoolPlayerLeaderboard("umass.edu", 42, LeaderboardService.Range.ALL_TIME);
        assertEquals(11, result.me().rank());
    }

    // ─── College domains ─────────────────────────────────────────────────────────

    @Test
    void collegeDomainResolution() {
        assertEquals("umass.edu", College.DomainCollegeMap.resolveDomain("umass.edu"));
        assertEquals("umass.edu", College.DomainCollegeMap.resolveDomain("cs.umass.edu"));
        assertEquals("cmu.edu", College.DomainCollegeMap.resolveDomain("Andrew.CMU.edu"));
        assertNull(College.DomainCollegeMap.resolveDomain("evilumass.edu"));
        assertNull(College.DomainCollegeMap.resolveDomain("umass.edu.evil.com"));
        assertNull(College.DomainCollegeMap.resolveDomain("edu"));
        assertNull(College.DomainCollegeMap.resolveDomain(""));
        assertNull(College.DomainCollegeMap.resolveDomain(null));
    }
}
