package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerAction;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.DealerService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.PotManagerService;
import com.collegeshowdown.poker_project.services.WinnerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full game-flow tests: blinds, betting rounds, folds, raises, all-ins with
 * side pots, departures, and chip conservation. Runs the real engine with a
 * no-op message channel — no Spring context, no WebSocket, no database.
 */
class GameEngineTest {

    private LobbyGameService game;
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        game = new LobbyGameService(
                new DealerService(),
                new PotManagerService(),
                new WinnerService(),
                new GameStateBuilder(),
                new SimpMessagingTemplate((message, timeout) -> true),
                null); // LobbyManagerService not needed for unit tests
        game.setRunOutStepMillis(5); // fast all-in run-outs in tests
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Lobby lowStakesLobby() {
        return new Lobby(LobbyType.GLOBAL, StakesLevel.LOW); // blinds 1/2
    }

    private ConnectedPlayer seat(Lobby lobby, int seat, String name, int chips) {
        PlayerRecord record = new PlayerRecord(name, name + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ConnectedPlayer player = new ConnectedPlayer(record);
        player.buyIn(chips);
        lobby.getPlayersAtTable()[seat] = player;
        return player;
    }

    private void act(Lobby lobby, PlayerAction action) {
        act(lobby, action, 0);
    }

    private void act(Lobby lobby, PlayerAction action, int amount) {
        game.processPlayerAction(lobby, lobby.getCurrentPlayerIndex(), action, amount);
    }

    /** Calls when facing a bet, otherwise checks — runs the hand to completion. */
    private void checkOrCallToShowdown(Lobby lobby) {
        for (int i = 0; i < 50 && lobby.getGamePhase() != GamePhase.HAND_COMPLETE; i++) {
            ConnectedPlayer current =
                    lobby.getPlayersAtTable()[lobby.getCurrentPlayerIndex()];
            boolean facingBet =
                    lobby.getRoundContribution(current) < lobby.getCurrentBetAmount();
            act(lobby, facingBet ? PlayerAction.CALL : PlayerAction.CHECK);
        }
    }

    /** All-in run-outs reveal streets asynchronously — poll until the hand ends. */
    private void awaitHandComplete(Lobby lobby) {
        for (int i = 0; i < 400 && lobby.getGamePhase() != GamePhase.HAND_COMPLETE; i++) {
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
    }

    private int potTotal(Lobby lobby) {
        return lobby.getAllActivePots().stream().mapToInt(Pot::getAmountInPot).sum();
    }

    private int totalChipsInPlay(Lobby lobby) {
        int stacks = Arrays.stream(lobby.getPlayersAtTable())
                .filter(Objects::nonNull)
                .mapToInt(ConnectedPlayer::getActiveChips)
                .sum();
        return stacks + potTotal(lobby);
    }

    // ─── Hand start ──────────────────────────────────────────────────────────────

    @Test
    void handStartsWithBlindsAndHoleCards() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer a = seat(lobby, 0, "alice", 200);
        ConnectedPlayer b = seat(lobby, 1, "bob", 200);

        game.startHand(lobby);

        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());
        assertEquals(3, potTotal(lobby)); // SB 1 + BB 2
        assertEquals(2, a.getCards().size());
        assertEquals(2, b.getCards().size());
        assertTrue(lobby.getSmallBlindSeat() >= 0);
        assertTrue(lobby.getBigBlindSeat() >= 0);
        assertEquals(2, lobby.getCurrentBetAmount());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void handDoesNotStartWithOneFundedPlayer() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "broke", 0);

        game.startHand(lobby);

        assertEquals(GamePhase.WAITING, lobby.getGamePhase());
    }

    // ─── Betting rounds ──────────────────────────────────────────────────────────

    @Test
    void checkedDownHandReachesShowdownWithFullBoard() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        game.startHand(lobby);

        checkOrCallToShowdown(lobby);

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(5, lobby.getBoard().size());
        assertFalse(lobby.getWinners().isEmpty());
        // Pot fully distributed; no chips created or destroyed
        assertEquals(0, potTotal(lobby));
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void foldEndsHandAndAwardsPotImmediately() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        int folder = lobby.getCurrentPlayerIndex();
        int winner = (folder == 0) ? 1 : 0;
        act(lobby, PlayerAction.FOLD);

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(1, lobby.getWinners().size());
        // Pre-flop heads-up: folder is SB (-1), winner nets +1
        assertEquals(199, lobby.getPlayersAtTable()[folder].getActiveChips());
        assertEquals(201, lobby.getPlayersAtTable()[winner].getActiveChips());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void raiseReopensActionAndIsCallable() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        act(lobby, PlayerAction.RAISE, 4); // call 1 + raise 4
        assertEquals(6, lobby.getCurrentBetAmount());
        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());

        act(lobby, PlayerAction.CALL);
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());
        assertEquals(12, potTotal(lobby)); // 6 each
        assertEquals(3, lobby.getBoard().size());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void illegalCheckIsRejected() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        int actor = lobby.getCurrentPlayerIndex(); // SB owes 1 more
        act(lobby, PlayerAction.CHECK);

        // Nothing changed: still the same player's turn, same phase
        assertEquals(actor, lobby.getCurrentPlayerIndex());
        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());
        assertEquals(3, potTotal(lobby));
    }

    @Test
    void outOfTurnActionIsIgnored() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        int actor = lobby.getCurrentPlayerIndex();
        int other = (actor == 0) ? 1 : 0;
        game.processPlayerAction(lobby, other, PlayerAction.FOLD, 0);

        assertEquals(actor, lobby.getCurrentPlayerIndex());
        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());
    }

    @Test
    void illegalRaiseBelowMinimumIsRejected() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        int actor = lobby.getCurrentPlayerIndex();
        act(lobby, PlayerAction.RAISE, 1); // below the minimum raise of 2 (the big blind)

        // Rejected: still the same player's turn, bet/raise size unchanged
        assertEquals(actor, lobby.getCurrentPlayerIndex());
        assertEquals(2, lobby.getCurrentBetAmount());
        assertEquals(2, lobby.getLastRaiseSize());
        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());

        // A legal raise from the same player still works afterwards — the
        // illegal attempt didn't leave the round in a stuck state
        act(lobby, PlayerAction.RAISE, 4);
        assertEquals(6, lobby.getCurrentBetAmount());
    }

    @Test
    void bigBlindGetsOptionAfterLimp() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        act(lobby, PlayerAction.CALL); // SB limps
        // BB must still get their option — hand must NOT have advanced yet
        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());
        assertEquals(lobby.getBigBlindSeat(), lobby.getCurrentPlayerIndex());

        act(lobby, PlayerAction.CHECK); // BB checks the option
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());
    }

    // ─── All-ins & side pots ─────────────────────────────────────────────────────

    @Test
    void allInHeadsUpRunsOutBoardToShowdown() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        act(lobby, PlayerAction.ALL_IN);
        act(lobby, PlayerAction.CALL);

        awaitHandComplete(lobby);
        assertEquals(5, lobby.getBoard().size());
        assertEquals(400, totalChipsInPlay(lobby));
        assertEquals(0, potTotal(lobby));
    }

    @Test
    void unevenStacksCreateCorrectSidePots() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer shortStack = seat(lobby, 0, "shorty", 50);
        ConnectedPlayer midStack   = seat(lobby, 1, "middy", 100);
        ConnectedPlayer bigStack   = seat(lobby, 2, "biggy", 200);
        game.startHand(lobby);

        // Everyone shoves
        act(lobby, PlayerAction.ALL_IN);
        act(lobby, PlayerAction.ALL_IN);
        act(lobby, PlayerAction.ALL_IN);

        awaitHandComplete(lobby);
        assertEquals(5, lobby.getBoard().size());

        // Conservation: 350 chips total, all distributed
        assertEquals(350, totalChipsInPlay(lobby));
        assertEquals(0, potTotal(lobby));

        // The short stack can win at most the main pot (3 × 50 = 150)
        assertTrue(shortStack.getActiveChips() <= 150,
                "short stack won more than the main pot: " + shortStack.getActiveChips());
        // The big stack's 100 overage is only winnable by them: they always
        // keep at least it (their uncalled excess comes straight back)
        assertTrue(bigStack.getActiveChips() >= 100,
                "big stack lost their uncalled overage: " + bigStack.getActiveChips());
        // The mid stack can win at most main + first side pot (150 + 150)
        assertTrue(midStack.getActiveChips() <= 300,
                "mid stack won a pot they weren't eligible for: " + midStack.getActiveChips());
    }

    @Test
    void foldedPlayersChipsStayInPotForOthers() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        // First to act raises, next folds, third calls
        act(lobby, PlayerAction.RAISE, 8);
        act(lobby, PlayerAction.FOLD);
        act(lobby, PlayerAction.CALL);

        // The folder's blind stays in the pot
        assertNotEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(600, totalChipsInPlay(lobby));

        checkOrCallToShowdown(lobby);
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(600, totalChipsInPlay(lobby));
    }

    // ─── Departures ──────────────────────────────────────────────────────────────

    @Test
    void lastOpponentLeavingConcludesHandForRemainingPlayer() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        int current = lobby.getCurrentPlayerIndex();
        int leaver = (current == 0) ? 1 : 0;
        game.handlePlayerDeparture(lobby, leaver);

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(1, lobby.getWinners().size());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void currentPlayerLeavingFoldsAndPlayContinues() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        int leaver = lobby.getCurrentPlayerIndex();
        game.handlePlayerDeparture(lobby, leaver);

        // Hand continues between the other two
        assertNotEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(PlayerState.FOLDED,
                lobby.getPlayersAtTable()[leaver].getPlayerState());
        assertNotEquals(leaver, lobby.getCurrentPlayerIndex());

        checkOrCallToShowdown(lobby);
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(600, totalChipsInPlay(lobby));
    }

    @Test
    void allInPlayerDepartureKeepsPotEquity() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer alice = seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        // 3-handed: carol (UTG) acts first preflop.
        assertEquals(2, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.CALL);

        // Alice (SB) shoves all-in.
        assertEquals(0, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.ALL_IN);
        assertEquals(PlayerState.ALL_IN, alice.getPlayerState());

        // She disconnects mid-hand while all-in — must not be folded out,
        // and seat removal must be deferred until the hand concludes.
        boolean canVacateNow = game.handlePlayerDeparture(lobby, 0);
        assertFalse(canVacateNow, "an ALL_IN departure must defer seat removal");
        assertEquals(PlayerState.ALL_IN, alice.getPlayerState(), "must not be folded out");
        assertTrue(lobby.getPendingDepartureSeats().contains(0));

        // Bob and carol both call off the rest of their stacks
        act(lobby, PlayerAction.CALL); // bob
        act(lobby, PlayerAction.CALL); // carol

        awaitHandComplete(lobby);

        // No chips vanished — alice's all-in stake was properly contested
        // for, not stripped from the pot by her disconnect.
        assertEquals(600, totalChipsInPlay(lobby));
    }

    // ─── Next hand reset ─────────────────────────────────────────────────────────

    @Test
    void nextHandFullyResetsState() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer a = seat(lobby, 0, "alice", 200);
        ConnectedPlayer b = seat(lobby, 1, "bob", 200);
        game.startHand(lobby);
        act(lobby, PlayerAction.FOLD); // quick hand 1

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());

        // Start hand 2 directly (production does this via the 5s scheduler)
        game.startHand(lobby);

        assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase());
        assertEquals(3, potTotal(lobby));
        assertEquals(0, lobby.getBoard().size());
        assertEquals(PlayerState.ACTIVE, a.getPlayerState());
        assertEquals(PlayerState.ACTIVE, b.getPlayerState());
        assertEquals(2, a.getCards().size());
        assertEquals(2, b.getCards().size());
        assertTrue(lobby.getWinners().isEmpty());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void midHandJoinerSitsOutUntilNextHand() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        game.startHand(lobby);

        // Carol joins mid-hand, marked folded (sitting out) like the manager does
        ConnectedPlayer carol = seat(lobby, 2, "carol", 200);
        carol.setPlayerState(PlayerState.FOLDED);

        checkOrCallToShowdown(lobby);
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(200, carol.getActiveChips()); // untouched by hand 1

        game.startHand(lobby);
        assertEquals(PlayerState.ACTIVE, carol.getPlayerState());
        assertEquals(2, carol.getCards().size()); // dealt in for hand 2
    }

    // ─── Blind rotation ──────────────────────────────────────────────────────────

    @Test
    void blindsRotateThroughOccupiedSeats() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 1, "bob", 500);
        seat(lobby, 2, "carol", 500);

        game.startHand(lobby);
        assertEquals(0, lobby.getSmallBlindSeat());
        assertEquals(1, lobby.getBigBlindSeat());
        checkOrCallToShowdown(lobby);

        game.startHand(lobby);
        assertEquals(1, lobby.getSmallBlindSeat());
        assertEquals(2, lobby.getBigBlindSeat());
        checkOrCallToShowdown(lobby);

        game.startHand(lobby);
        assertEquals(2, lobby.getSmallBlindSeat());
        assertEquals(0, lobby.getBigBlindSeat());
    }

    @Test
    void blindsRotateAcrossSeatGaps() {
        // Players in non-adjacent seats — rotation must follow occupied seats
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 5, "bob", 500);

        game.startHand(lobby);
        assertEquals(0, lobby.getSmallBlindSeat());
        assertEquals(5, lobby.getBigBlindSeat());
        act(lobby, PlayerAction.FOLD);

        game.startHand(lobby);
        assertEquals(5, lobby.getSmallBlindSeat());
        assertEquals(0, lobby.getBigBlindSeat());
        act(lobby, PlayerAction.FOLD);

        game.startHand(lobby);
        assertEquals(0, lobby.getSmallBlindSeat());
        assertEquals(5, lobby.getBigBlindSeat());
    }

    // ─── Multi-hand stability (regression: frozen lobby after fold-win) ─────────

    @Test
    void manyConsecutiveHandsDoNotCorruptState() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 1, "bob", 500);

        for (int hand = 0; hand < 10; hand++) {
            game.startHand(lobby);
            assertEquals(GamePhase.PRE_FLOP, lobby.getGamePhase(),
                    "hand " + hand + " failed to start");

            if (hand % 2 == 0) {
                act(lobby, PlayerAction.FOLD);       // fold-win path
            } else {
                checkOrCallToShowdown(lobby);        // showdown path
            }
            assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase(),
                    "hand " + hand + " failed to complete");
            assertEquals(1000, totalChipsInPlay(lobby),
                    "chips not conserved after hand " + hand);
        }
    }

    // ─── Helper for the common two-player setup ──────────────────────────────────

    private Lobby seatTwo(Lobby lobby, int chipsA, int chipsB) {
        seat(lobby, 0, "alice", chipsA);
        seat(lobby, 1, "bob", chipsB);
        return lobby;
    }
}
