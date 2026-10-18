package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.services.DealerService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.PotManagerService;
import com.collegeshowdown.poker_project.services.WinnerService;
import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.player.PlayerAction;
import com.collegeshowdown.poker_project.service.GameStateBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.collegeshowdown.poker_project.domain.card.Rank.*;
import static com.collegeshowdown.poker_project.domain.card.Suit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Side-pot construction and showdown payout, driven directly through
 * PotManagerService + WinnerService with hand-built contributions and cards.
 */
class PotAndShowdownTest {

    private final PotManagerService potManager = new PotManagerService();
    private final WinnerService winnerService = new WinnerService();
    private Lobby lobby;
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.LOW);
    }

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    /** Seats a player who has put {@code contributed} into the pot this hand. */
    private ConnectedPlayer seat(int seat, int contributed, PlayerState state, Card... hole) {
        PlayerRecord record = new PlayerRecord("p" + nextId, "p" + nextId + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ConnectedPlayer p = new ConnectedPlayer(record);
        p.setCards(new ArrayList<>(List.of(hole)));
        p.setPlayerState(state);
        lobby.getPlayersAtTable()[seat] = p;
        if (contributed > 0) lobby.addHandContribution(p, contributed);
        return p;
    }

    private void board(Card... cards) {
        lobby.getBoard().addAll(List.of(cards));
    }

    private int potTotal() {
        return lobby.getAllActivePots().stream().mapToInt(Pot::getAmountInPot).sum();
    }

    private void showdown() {
        potManager.recalculatePots(lobby);
        winnerService.determineWinners(lobby);
    }

    // A dry board that plays for everyone: split pots
    private void splitBoard() {
        board(c(ACE, HEARTS), c(KING, SPADES), c(QUEEN, DIAMONDS), c(JACK, CLUBS), c(TEN, HEARTS));
    }

    @Test
    void sidePotsAcrossMultipleAllInLevelsConserveChips() {
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 300, PlayerState.ALL_IN, c(KING, HEARTS), c(KING, CLUBS));
        ConnectedPlayer cc = seat(2, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        ConnectedPlayer d = seat(3, 500, PlayerState.ACTIVE, c(THREE, HEARTS), c(EIGHT, CLUBS));
        ConnectedPlayer folded = seat(4, 200, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        potManager.recalculatePots(lobby);
        assertEquals(1600, potTotal());
        winnerService.determineWinners(lobby);

        // A (trip aces): 100 x 5 = 500. B (trip kings): 100 x 4 (incl. the
        // folded 100) + 100 x 3 = 700. C/D contest the 200 x 2 = 400 top level.
        assertEquals(500, a.getActiveChips());
        assertEquals(700, b.getActiveChips());
        assertEquals(400, cc.getActiveChips() + d.getActiveChips());
        assertEquals(0, folded.getActiveChips());
        assertEquals(0, potTotal());
    }

    @Test
    void oddChipGoesToFirstWinnerLeftOfButton() {
        lobby.setSmallBlindSeat(5);
        // Seats 2 and 6 split; a folded player's single chip makes the pot odd.
        ConnectedPlayer seat2 = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer seat6 = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        seat(5, 1, PlayerState.FOLDED);
        splitBoard();

        showdown();

        // Clockwise from the small blind (seat 5): seat 6 comes before seat 2
        assertEquals(11, seat6.getActiveChips());
        assertEquals(10, seat2.getActiveChips());
    }

    @Test
    void oddChipOrderDoesNotDependOnPlayerIds() {
        // Same table as above, but seat ids created in the opposite order
        lobby.setSmallBlindSeat(5);
        ConnectedPlayer seat6 = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        ConnectedPlayer seat2 = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        seat(5, 1, PlayerState.FOLDED);
        splitBoard();

        showdown();

        assertEquals(11, seat6.getActiveChips());
        assertEquals(10, seat2.getActiveChips());
    }

    @Test
    void evenSplitIsNotSkewedByDeadMoneySlices() {
        // Three players fold after putting in 1, 2 and 3 chips; the two
        // remaining players split 26 chips — must be 13 / 13, not 14 / 12.
        lobby.setSmallBlindSeat(0);
        seat(0, 1, PlayerState.FOLDED);
        seat(1, 2, PlayerState.FOLDED);
        seat(2, 3, PlayerState.FOLDED);
        ConnectedPlayer x = seat(3, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer y = seat(4, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        splitBoard();

        showdown();

        assertEquals(13, x.getActiveChips());
        assertEquals(13, y.getActiveChips());
    }

    @Test
    void uncalledExcessReturnedButBettorIsNotAWinner() {
        // Short stack all-in for 100 and wins; the big stack's uncalled 400
        // comes back to them, but they lost the hand.
        ConnectedPlayer shortStack = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer bigStack = seat(1, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(200, shortStack.getActiveChips());
        assertEquals(400, bigStack.getActiveChips());
        assertEquals(List.of(shortStack), lobby.getWinners());
    }

    @Test
    void sidePotWonFromFoldedPlayerStillCountsAsWin() {
        // C folds to B after both put 500 in; A all-in for 100 wins the main pot.
        // B collects C's dead money from the side pot, which is a real win.
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 500, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        seat(2, 500, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(300, a.getActiveChips());
        assertEquals(800, b.getActiveChips());
        assertTrue(lobby.getWinners().contains(a));
        assertTrue(lobby.getWinners().contains(b));
    }

    @Test
    void bustedSeatWithoutHoleCardsIsNotGivenTheBoard() {
        // A seated player who busted last hand stays ALL_IN with no cards
        ConnectedPlayer busted = seat(0, 0, PlayerState.ALL_IN);
        ConnectedPlayer a = seat(1, 50, PlayerState.ACTIVE, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(2, 50, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertTrue(busted.getCards().isEmpty(), "board cards must not become a busted seat's hole cards");
        assertEquals(100, a.getActiveChips());
        assertEquals(0, b.getActiveChips());
    }

    @Test
    void headsUpOddChipGoesToBigBlind() {
        // Heads-up the button posts the small blind; first seat left of it is the BB
        lobby.setHeadsUpHand(true);
        lobby.setSmallBlindSeat(2);
        lobby.setBigBlindSeat(6);
        ConnectedPlayer sb = seat(2, 10, PlayerState.ACTIVE, c(TWO, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer bb = seat(6, 10, PlayerState.ACTIVE, c(TWO, SPADES), c(THREE, DIAMONDS));
        seat(4, 1, PlayerState.FOLDED); // a departed player's dead chip makes it odd
        splitBoard();

        showdown();

        assertEquals(11, bb.getActiveChips());
        assertEquals(10, sb.getActiveChips());
    }

    @Test
    void potWhoseContestantsAllFoldedAfterRecalcIsNotDestroyed() {
        // C bets 200, B all-in calls 100, A all-in 50: C's top 100 is a pot
        // only C contests. C then leaves during the run-out (folded, no recalc).
        ConnectedPlayer a = seat(0, 50, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 100, PlayerState.ALL_IN, c(KING, HEARTS), c(KING, CLUBS));
        ConnectedPlayer cc = seat(2, 200, PlayerState.ACTIVE, c(TWO, HEARTS), c(SEVEN, CLUBS));
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        potManager.recalculatePots(lobby);
        cc.foldCards(lobby.getAllActivePots());
        winnerService.determineWinners(lobby);

        assertEquals(350, a.getActiveChips() + b.getActiveChips(), "all 350 chips must be paid out");
        assertEquals(0, potTotal());
    }

    @Test
    void foldedPlayersHighestContributionIsNotLost() {
        // Folded player put in more than everyone still in the hand
        ConnectedPlayer a = seat(0, 100, PlayerState.ALL_IN, c(ACE, SPADES), c(ACE, CLUBS));
        ConnectedPlayer b = seat(1, 100, PlayerState.ALL_IN, c(TWO, HEARTS), c(SEVEN, CLUBS));
        seat(2, 300, PlayerState.FOLDED);
        board(c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS), c(FOUR, CLUBS), c(SIX, HEARTS));

        showdown();

        assertEquals(500, a.getActiveChips() + b.getActiveChips());
        assertEquals(500, a.getActiveChips());
    }

    // ─── Randomized chip conservation through the real engine ───────────────────

    /** Chips that entered / left a table, for the conservation check. */
    private static final class Ledger {
        long boughtIn;
        long cashedOut;
        final List<PlayerRecord> departed = new ArrayList<>();
    }

    private static PlayerRecord record(int id) {
        PlayerRecord record = new PlayerRecord("r" + id, "r" + id + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return record;
    }

    /**
     * Plays thousands of random hands — short and uneven stacks, random
     * folds / calls / raises / shoves, illegal actions, players leaving and
     * (re)joining mid-hand and during all-in run-outs, mid-hand rebuys — and
     * after every single step asserts that no chip was created or destroyed:
     * stacks + pots + cashed-out == bought-in. Also checks the pots always
     * match the recorded contributions, every pot is paid out by the end of
     * the hand, and no card is ever dealt twice in a hand.
     */
    @Test
    void randomHandsConserveChips() throws InterruptedException {
        LobbyGameService engine = new LobbyGameService(new DealerService(), potManager, winnerService,
                new GameStateBuilder(), new SimpMessagingTemplate((message, timeout) -> true), null);
        engine.setRunOutStepMillis(0);
        Random rng = new Random(424242L);
        int[] ids = {10_000};

        for (int table = 0; table < 150; table++) {
            StakesLevel stakes = StakesLevel.values()[rng.nextInt(StakesLevel.values().length)];
            Lobby lobby = new Lobby(LobbyType.GLOBAL, stakes);
            Ledger ledger = new Ledger();

            for (int hand = 0; hand < 12; hand++) {
                // Keep at least two funded players seated
                synchronized (lobby) {
                    long funded = java.util.Arrays.stream(lobby.getPlayersAtTable())
                            .filter(p -> p != null && p.getActiveChips() > 0).count();
                    while (funded < 2 + rng.nextInt(3) && lobby.tableCount() < Lobby.TABLE_SIZE) {
                        joinRandom(lobby, rng, ledger, ids);
                        funded++;
                    }
                }
                engine.startHand(lobby);
                assertConserved(lobby, ledger);

                for (int step = 0; ; step++) {
                    assertTrue(step < 2000, "hand never finished");
                    synchronized (lobby) {
                        if (lobby.getGamePhase() == GamePhase.HAND_COMPLETE
                                || lobby.getGamePhase() == GamePhase.WAITING) break;
                    }
                    int roll = rng.nextInt(100);
                    if (roll < 4) {
                        depart(engine, lobby, rng, ledger);
                    } else if (roll < 6) {
                        synchronized (lobby) {
                            if (lobby.tableCount() < Lobby.TABLE_SIZE) joinRandom(lobby, rng, ledger, ids);
                        }
                    } else if (roll < 8) {
                        rebuyRandom(lobby, rng, ledger);
                    } else if (lobby.getCurrentPlayerIndex() == -1) {
                        Thread.sleep(1); // all-in run-out in progress
                    } else {
                        randomAction(engine, lobby, rng);
                    }
                    assertConserved(lobby, ledger);
                }

                synchronized (lobby) {
                    for (Pot pot : lobby.getAllActivePots()) {
                        assertEquals(0, pot.getAmountInPot(), "pot left unpaid at end of hand");
                    }
                    // Emulates LobbyManagerService.leaveTable for seats that left while all-in
                    for (int seat : new ArrayList<>(lobby.getPendingDepartureSeats())) {
                        ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
                        if (p == null) continue;
                        ledger.cashedOut += p.getActiveChips();
                        ledger.departed.add(p.playerRecord);
                        lobby.getPlayersAtTable()[seat] = null;
                    }
                    lobby.getPendingDepartureSeats().clear();
                    assertConserved(lobby, ledger);
                }
            }
        }
    }

    private void joinRandom(Lobby lobby, Random rng, Ledger ledger, int[] ids) {
        int seat = -1;
        for (int i = 0; i < Lobby.TABLE_SIZE; i++) if (lobby.getPlayersAtTable()[i] == null) { seat = i; break; }
        if (seat == -1) return;
        // Sometimes the same account comes back (possibly in the hand it left)
        PlayerRecord rec = !ledger.departed.isEmpty() && rng.nextInt(3) == 0
                ? ledger.departed.remove(rng.nextInt(ledger.departed.size()))
                : record(ids[0]++);
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.playerRecord.getId().equals(rec.getId())) return; // already seated
        }
        int bb = lobby.getBigBlind();
        int chips = switch (rng.nextInt(4)) {
            case 0 -> 1 + rng.nextInt(bb + 1);       // shorter than the blinds
            case 1 -> 1 + rng.nextInt(10 * bb);
            default -> 1 + rng.nextInt(100 * bb);
        };
        ConnectedPlayer p = new ConnectedPlayer(rec);
        p.buyIn(chips);
        if (lobby.getGamePhase() != GamePhase.WAITING) p.setPlayerState(PlayerState.FOLDED);
        lobby.getPlayersAtTable()[seat] = p;
        ledger.boughtIn += chips;
    }

    /** Mirrors WebSocketsController.removePlayerFromLobby + leaveTable's cash-out. */
    private void depart(LobbyGameService engine, Lobby lobby, Random rng, Ledger ledger) {
        synchronized (lobby) {
            int seat = rng.nextInt(Lobby.TABLE_SIZE);
            ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
            if (p == null) return;
            if (engine.handlePlayerDeparture(lobby, seat)) {
                ledger.cashedOut += p.getActiveChips();
                ledger.departed.add(p.playerRecord);
                lobby.getPlayersAtTable()[seat] = null;
            }
        }
    }

    /** Mirrors LobbyManagerService.rebuy's table-stakes rule. */
    private void rebuyRandom(Lobby lobby, Random rng, Ledger ledger) {
        synchronized (lobby) {
            int seat = rng.nextInt(Lobby.TABLE_SIZE);
            ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
            if (p == null) return;
            boolean handInProgress = lobby.getGamePhase() != GamePhase.WAITING
                    && lobby.getGamePhase() != GamePhase.HAND_COMPLETE;
            boolean inCurrentHand = handInProgress && p.getPlayerState() != PlayerState.FOLDED
                    && (!p.getCards().isEmpty() || lobby.getHandContributions().getOrDefault(p, 0) > 0);
            if (inCurrentHand) return;
            int amount = 1 + rng.nextInt(20 * lobby.getBigBlind());
            p.addChips(amount);
            ledger.boughtIn += amount;
            if (handInProgress) p.setPlayerState(PlayerState.FOLDED);
        }
    }

    private void randomAction(LobbyGameService engine, Lobby lobby, Random rng) {
        int seat;
        PlayerAction action;
        int amount = 0;
        synchronized (lobby) {
            seat = lobby.getCurrentPlayerIndex();
            if (seat < 0) return;
            ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
            if (p == null) return;
            int owed = lobby.getCurrentBetAmount() - lobby.getRoundContribution(p);
            int roll = rng.nextInt(100);
            if (roll < 15) action = PlayerAction.FOLD;
            else if (roll < 55) action = owed > 0 ? PlayerAction.CALL : PlayerAction.CHECK;
            else if (roll < 60) action = PlayerAction.CHECK; // illegal when facing a bet
            else if (roll < 85) {
                action = PlayerAction.RAISE;
                amount = rng.nextInt(3) == 0
                        ? rng.nextInt(lobby.getLastRaiseSize() + 1)            // often below the minimum
                        : lobby.getLastRaiseSize() + rng.nextInt(4 * lobby.getBigBlind() + 1);
            } else action = PlayerAction.ALL_IN;
        }
        engine.processPlayerAction(lobby, seat, action, amount);
    }

    private void assertConserved(Lobby lobby, Ledger ledger) {
        synchronized (lobby) {
            long stacks = 0;
            Set<Card> dealt = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Set<String> faces = new HashSet<>();
            for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
                if (p == null) continue;
                assertTrue(p.getActiveChips() >= 0, "negative stack");
                stacks += p.getActiveChips();
                dealt.addAll(p.getCards().stream().limit(2).toList());
            }
            dealt.addAll(lobby.getBoard());
            for (Card card : dealt) {
                assertTrue(faces.add(card.getRank() + " " + card.getSuit()), "card dealt twice: " + card);
            }
            long pots = lobby.getAllActivePots().stream().mapToLong(Pot::getAmountInPot).sum();
            assertEquals(ledger.boughtIn, stacks + pots + ledger.cashedOut,
                    "chips created or destroyed (stacks=" + stacks + ", pots=" + pots + ")");

            boolean handInProgress = lobby.getGamePhase() != GamePhase.WAITING
                    && lobby.getGamePhase() != GamePhase.HAND_COMPLETE;
            if (handInProgress) {
                long contributed = lobby.getHandContributions().values().stream().mapToLong(Integer::longValue).sum();
                assertEquals(contributed, pots, "pots don't match contributions");
            }
        }
    }
}
