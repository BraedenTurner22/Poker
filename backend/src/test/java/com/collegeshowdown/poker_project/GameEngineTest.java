package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerAction;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.dto.GameStateDto;
import com.collegeshowdown.poker_project.dto.PlayerStateDto;
import com.collegeshowdown.poker_project.dto.PrivateStateDto;
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
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full game-flow tests: blinds, betting rounds, folds, raises, all-ins with
 * side pots, departures, and chip conservation. Runs the real engine with a
 * no-op message channel — no Spring context, no WebSocket, no database.
 */
class GameEngineTest {

    private LobbyGameService game;
    private GameStateBuilder stateBuilder;
    private int nextId = 1;

    @BeforeEach
    void setUp() {
        stateBuilder = new GameStateBuilder();
        game = new LobbyGameService(
                new DealerService(),
                new PotManagerService(),
                new WinnerService(),
                stateBuilder,
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

    // ─── Card visibility (showdown reveal) ──────────────────────────────────────

    @Test
    void allInOpponentCardsStayHiddenUntilCalled() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));

        act(lobby, PlayerAction.CALL);  // SB limps preflop
        act(lobby, PlayerAction.CHECK); // BB takes the option -> FLOP
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());

        int shover = lobby.getCurrentPlayerIndex();
        int caller = (shover == 0) ? 1 : 0;
        act(lobby, PlayerAction.ALL_IN);

        // The caller still has a pending decision — cards must stay hidden
        // until they actually call, not the instant the shove lands.
        GameStateDto stateBeforeCall = stateBuilder.build(lobby);
        assertTrue(playerDto(stateBeforeCall, shover).holeCards().isEmpty(),
                "opponent's cards were revealed before the all-in was called");

        act(lobby, PlayerAction.CALL);
        awaitHandComplete(lobby);

        GameStateDto finalState = stateBuilder.build(lobby);
        assertEquals(2, playerDto(finalState, shover).holeCards().size());
        assertEquals(2, playerDto(finalState, caller).holeCards().size());
    }

    @Test
    void riverCallWithChipsBehindRevealsExactlyTwoHoleCardsEach() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer a = seat(lobby, 0, "alice", 200);
        ConnectedPlayer b = seat(lobby, 1, "bob", 200);
        game.startHand(lobby);

        act(lobby, PlayerAction.CALL);  // preflop limp
        act(lobby, PlayerAction.CHECK); // preflop BB option -> FLOP
        act(lobby, PlayerAction.CHECK);
        act(lobby, PlayerAction.CHECK); // -> TURN
        act(lobby, PlayerAction.CHECK);
        act(lobby, PlayerAction.CHECK); // -> RIVER
        assertEquals(GamePhase.RIVER, lobby.getGamePhase());

        act(lobby, PlayerAction.RAISE, 20); // a river bet
        act(lobby, PlayerAction.CALL);      // a normal call, chips left behind

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(PlayerState.ACTIVE, a.getPlayerState());
        assertEquals(PlayerState.ACTIVE, b.getPlayerState());

        // WinnerService.determineWinners appends the 5 board cards onto each
        // player's hand for evaluation — the broadcast must still only expose
        // the 2 actual hole cards, not the merged 7-card evaluation hand.
        GameStateDto state = stateBuilder.build(lobby);
        assertEquals(2, playerDto(state, 0).holeCards().size(),
                "exposed hand leaked board cards merged in by WinnerService");
        assertEquals(2, playerDto(state, 1).holeCards().size(),
                "opponent's cards were not shown at river-call showdown");

        PrivateStateDto privateA = stateBuilder.buildPrivate(a);
        PrivateStateDto privateB = stateBuilder.buildPrivate(b);
        assertEquals(2, privateA.holeCards().size());
        assertEquals(2, privateB.holeCards().size());
    }

    private PlayerStateDto playerDto(GameStateDto state, int seat) {
        return state.players().stream()
                .filter(p -> p.seat() == seat)
                .findFirst()
                .orElseThrow();
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

    @Test
    void playerLeavingDuringAllInRunOutKeepsTheirCalledStake() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 100);
        ConnectedPlayer bob = seat(lobby, 1, "bob", 300);
        game.startHand(lobby);

        synchronized (lobby) { // hold the run-out until bob has left
            act(lobby, PlayerAction.ALL_IN); // alice (SB) shoves 100
            act(lobby, PlayerAction.CALL);   // bob calls, 200 behind
            assertEquals(-1, lobby.getCurrentPlayerIndex(), "board is running out");

            // Bob has nothing left to decide; leaving must not fold his 100
            // in the pot over to alice.
            assertFalse(game.handlePlayerDeparture(lobby, 1));
            assertTrue(lobby.getPendingDepartureSeats().contains(1));
            assertEquals(PlayerState.ACTIVE, bob.getPlayerState());
        }

        awaitHandComplete(lobby);
        assertEquals(5, lobby.getBoard().size(), "hand goes to showdown, not a fold win");
        assertEquals(400, totalChipsInPlay(lobby));
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

    // ─── Regression: action order ────────────────────────────────────────────────

    @Test
    void headsUpBigBlindActsFirstPostflop() {
        Lobby lobby = lowStakesLobby();
        game.startHand(seatTwo(lobby, 200, 200));
        int sb = lobby.getSmallBlindSeat();
        int bb = lobby.getBigBlindSeat();

        // Heads-up: the button posts the small blind and acts first preflop...
        assertEquals(sb, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.CALL);
        act(lobby, PlayerAction.CHECK);
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());

        // ...but last on every postflop street
        assertEquals(bb, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.CHECK);
        assertEquals(sb, lobby.getCurrentPlayerIndex());
    }

    @Test
    void multiwayPostflopStillStartsWithSmallBlind() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        act(lobby, PlayerAction.CALL);  // carol (UTG)
        act(lobby, PlayerAction.CALL);  // alice (SB)
        act(lobby, PlayerAction.CHECK); // bob (BB)
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());
        assertEquals(0, lobby.getCurrentPlayerIndex());
    }

    // ─── Regression: busted players ──────────────────────────────────────────────

    /** A player who lost an all-in is left ALL_IN with 0 chips when the hand ends. */
    private ConnectedPlayer seatBusted(Lobby lobby, int seat, String name) {
        ConnectedPlayer p = seat(lobby, seat, name, 0);
        p.setPlayerState(PlayerState.ALL_IN);
        return p;
    }

    @Test
    void bustedPlayerIsNotCountedAsLiveNextHand() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer busted = seatBusted(lobby, 0, "busted");
        seat(lobby, 1, "alice", 200);
        seat(lobby, 2, "bob", 200);
        game.startHand(lobby);

        assertTrue(busted.getCards().isEmpty());
        assertNotEquals(PlayerState.ALL_IN, busted.getPlayerState());

        // A fold between the only two real players must end the hand at once
        act(lobby, PlayerAction.FOLD);
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(400, totalChipsInPlay(lobby));
        assertFalse(lobby.getWinners().contains(busted));
    }

    @Test
    void bustedPlayerRebuyingMidHandIsNotPulledIntoTheHand() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer busted = seatBusted(lobby, 0, "busted");
        seat(lobby, 1, "alice", 200);
        seat(lobby, 2, "bob", 200);
        game.startHand(lobby);

        busted.addChips(100); // what LobbyManagerService.rebuy does
        assertNotEquals(PlayerState.ACTIVE, busted.getPlayerState());

        checkOrCallToShowdown(lobby);
        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(100, busted.getActiveChips());

        game.startHand(lobby); // dealt in normally once funded
        assertEquals(PlayerState.ACTIVE, busted.getPlayerState());
        assertEquals(2, busted.getCards().size());
    }

    // ─── Regression: raise sizing ────────────────────────────────────────────────

    @Test
    void raiseLargerThanStackSetsBetToWhatWasActuallyPutIn() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "shorty", 50);
        ConnectedPlayer big = seat(lobby, 1, "biggy", 200);
        game.startHand(lobby);

        act(lobby, PlayerAction.RAISE, 1000); // SB only has 50
        assertEquals(50, lobby.getCurrentBetAmount());

        act(lobby, PlayerAction.CALL);
        // BB only had to call the 48 actually bet, not a phantom 1002
        awaitHandComplete(lobby);
        assertEquals(250, totalChipsInPlay(lobby));
        assertTrue(big.getActiveChips() >= 150);
    }

    @Test
    void hugeRaiseAmountDoesNotOverflow() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer a = seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        game.startHand(lobby);

        act(lobby, PlayerAction.RAISE, Integer.MAX_VALUE);
        assertEquals(PlayerState.ALL_IN, a.getPlayerState());
        assertEquals(200, lobby.getCurrentBetAmount());
        assertEquals(400, totalChipsInPlay(lobby));
        assertTrue(potTotal(lobby) > 0);
    }

    @Test
    void shortRaiseFromStackThatCantAffordMinRaiseGoesAllIn() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);           // SB
        ConnectedPlayer b = seat(lobby, 1, "bob", 20); // BB, 18 behind
        game.startHand(lobby);

        act(lobby, PlayerAction.RAISE, 10); // alice to 12
        // bob faces 10 more; a full re-raise needs 20 more — he only has 18
        act(lobby, PlayerAction.RAISE, 1);

        assertEquals(PlayerState.ALL_IN, b.getPlayerState());
        assertEquals(0, b.getActiveChips());
        assertEquals(20, lobby.getCurrentBetAmount());
        assertEquals(10, lobby.getLastRaiseSize()); // short raise doesn't shrink the min
    }

    @Test
    void shortAllInDoesNotReopenRaisingForPlayersWhoAlreadyActed() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500); // SB
        seat(lobby, 1, "bob", 500);   // BB
        seat(lobby, 2, "carol", 500); // UTG
        seat(lobby, 3, "dave", 14);
        game.startHand(lobby);

        assertEquals(2, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.RAISE, 8);  // carol raises to 10 (full raise of 8)
        act(lobby, PlayerAction.ALL_IN);    // dave all-in to 14: only +4, incomplete
        assertEquals(14, lobby.getCurrentBetAmount());
        act(lobby, PlayerAction.CALL);      // alice (hadn't acted) calls 14
        act(lobby, PlayerAction.CALL);      // bob calls 14

        // carol only faces an incomplete raise — she may call or fold, not re-raise
        assertEquals(2, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.RAISE, 20);
        assertEquals(14, lobby.getCurrentBetAmount());
        assertEquals(2, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.ALL_IN);
        assertEquals(14, lobby.getCurrentBetAmount());
        assertEquals(2, lobby.getCurrentPlayerIndex());

        act(lobby, PlayerAction.CALL);
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());
        assertEquals(1514, totalChipsInPlay(lobby));
    }

    @Test
    void playerWhoHadNotActedMayStillRaiseAfterShortAllIn() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 1, "bob", 500);
        seat(lobby, 2, "carol", 500);
        seat(lobby, 3, "dave", 14);
        game.startHand(lobby);

        act(lobby, PlayerAction.RAISE, 8); // carol to 10
        act(lobby, PlayerAction.ALL_IN);   // dave to 14 (incomplete)
        act(lobby, PlayerAction.RAISE, 8); // alice to 22 — a full raise
        assertEquals(22, lobby.getCurrentBetAmount());
        act(lobby, PlayerAction.CALL);     // bob

        // A full raise behind her reopens carol's option to raise
        assertEquals(2, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.RAISE, 8);
        assertEquals(30, lobby.getCurrentBetAmount());
    }

    // ─── Regression: blinds leave nobody to act ──────────────────────────────────

    @Test
    void handStartsWhenEveryStackIsShorterThanTheSmallBlind() {
        Lobby lobby = new Lobby(LobbyType.GLOBAL, StakesLevel.MEDIUM); // blinds 2/5
        seat(lobby, 0, "alice", 1);
        seat(lobby, 1, "bob", 1);
        game.startHand(lobby);

        awaitHandComplete(lobby);
        assertEquals(2, totalChipsInPlay(lobby));
    }

    @Test
    void bothBlindsAllInOnPostingRunsOutInsteadOfCrashing() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 1); // SB, all-in posting 1
        seat(lobby, 1, "bob", 2);   // BB, all-in posting 2
        game.startHand(lobby);

        awaitHandComplete(lobby);
        assertEquals(5, lobby.getBoard().size());
        assertEquals(3, totalChipsInPlay(lobby));
    }

    @Test
    void bigBlindWithNothingToDecideDoesNotWaitForAction() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 1);   // SB, all-in posting 1
        seat(lobby, 1, "bob", 200);   // BB covers it — no decision to make
        game.startHand(lobby);

        awaitHandComplete(lobby);
        assertEquals(201, totalChipsInPlay(lobby));
    }

    @Test
    void playerAllInFromPostingBlindIsStillDealtIn() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 1);   // SB, all-in posting 1
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        // Blinds are posted before the deal — the all-in poster must still get cards
        assertEquals(2, lobby.getPlayersAtTable()[0].getCards().size());
        assertEquals(PlayerState.ALL_IN, lobby.getPlayersAtTable()[0].getPlayerState());
    }

    // ─── Regression: stale turn timer ────────────────────────────────────────────

    private void fireTurnTimer(Lobby lobby, long generation, int seat) throws Exception {
        java.lang.reflect.Method m = LobbyGameService.class.getDeclaredMethod(
                "onTurnTimeout", Lobby.class, long.class, int.class);
        m.setAccessible(true);
        m.invoke(game, lobby, generation, seat);
    }

    @Test
    void staleTurnTimerDoesNotActOnTheNextPlayer() throws Exception {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);

        long staleGeneration = lobby.getTurnTimerGeneration();
        int staleSeat = lobby.getCurrentPlayerIndex();
        act(lobby, PlayerAction.CALL); // acts just as their timer fires
        int nextSeat = lobby.getCurrentPlayerIndex();

        // The old timer, already running when cancelled, finally gets the lock
        fireTurnTimer(lobby, staleGeneration, staleSeat);

        assertEquals(nextSeat, lobby.getCurrentPlayerIndex());
        assertEquals(PlayerState.ACTIVE, lobby.getPlayersAtTable()[nextSeat].getPlayerState());

        // The live timer still works
        fireTurnTimer(lobby, lobby.getTurnTimerGeneration(), nextSeat);
        assertEquals(PlayerState.FOLDED, lobby.getPlayersAtTable()[nextSeat].getPlayerState());
    }

    // ─── Regression: blind rotation when players come and go ────────────────────

    @Test
    void playerSittingDownBetweenBlindsDoesNotMakeBigBlindPostTwice() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 2, "carol", 500);

        game.startHand(lobby);
        assertEquals(0, lobby.getSmallBlindSeat());
        assertEquals(2, lobby.getBigBlindSeat());
        act(lobby, PlayerAction.FOLD);

        seat(lobby, 1, "bob", 500); // fills the gap between last hand's blinds
        game.startHand(lobby);
        assertNotEquals(2, lobby.getBigBlindSeat(), "carol must not post the big blind twice in a row");
        assertEquals(0, lobby.getBigBlindSeat());
        assertEquals(2, lobby.getSmallBlindSeat());
    }

    @Test
    void bigBlindLeavingDoesNotLetTheNextPlayerSkipIt() {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 1, "bob", 500);
        seat(lobby, 2, "carol", 500);
        seat(lobby, 3, "dave", 500);

        game.startHand(lobby);
        assertEquals(1, lobby.getBigBlindSeat());
        checkOrCallToShowdown(lobby);

        lobby.getPlayersAtTable()[1] = null; // last hand's big blind leaves
        game.startHand(lobby);
        assertEquals(2, lobby.getBigBlindSeat(), "carol was next in line for the big blind");
        assertEquals(0, lobby.getSmallBlindSeat());
    }

    // ─── Regression: departing with an uncalled bet ──────────────────────────────

    @Test
    void lastOpponentLeavingWithUncalledBetTakesItBack() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer alice = seat(lobby, 0, "alice", 200);
        ConnectedPlayer bob = seat(lobby, 1, "bob", 200);
        game.startHand(lobby);
        act(lobby, PlayerAction.CALL);  // alice (SB/button) completes
        act(lobby, PlayerAction.CHECK); // bob
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());

        assertEquals(1, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.RAISE, 50); // bob bets 50, nobody calls
        game.handlePlayerDeparture(lobby, 1);

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(198, bob.getActiveChips(), "bob's uncalled 50 must go back to him");
        assertEquals(202, alice.getActiveChips());
        assertEquals(400, totalChipsInPlay(lobby));
    }

    @Test
    void bettorLeavingMultiwayDoesNotLeaveAPhantomBet() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer alice = seat(lobby, 0, "alice", 200);
        seat(lobby, 1, "bob", 200);
        seat(lobby, 2, "carol", 200);
        game.startHand(lobby);
        act(lobby, PlayerAction.CALL);  // carol
        act(lobby, PlayerAction.CALL);  // alice
        act(lobby, PlayerAction.CHECK); // bob
        assertEquals(GamePhase.FLOP, lobby.getGamePhase());

        assertEquals(0, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.RAISE, 40); // alice bets 40
        game.handlePlayerDeparture(lobby, 0);

        assertEquals(198, alice.getActiveChips());
        assertEquals(0, lobby.getCurrentBetAmount());
        assertEquals(6, potTotal(lobby));
        assertEquals(1, lobby.getCurrentPlayerIndex());
        act(lobby, PlayerAction.CHECK); // bob may simply check now
        act(lobby, PlayerAction.CHECK); // carol
        assertEquals(GamePhase.TURN, lobby.getGamePhase());
    }

    // ─── Regression: inactivity streaks follow the player, not the seat ─────────

    @SuppressWarnings("unchecked")
    @Test
    void newcomerDoesNotInheritPreviousSeatOccupantsMissedHands() throws Exception {
        Lobby lobby = lowStakesLobby();
        seat(lobby, 0, "alice", 500);
        seat(lobby, 1, "bob", 500);
        seat(lobby, 2, "carol", 500);
        java.lang.reflect.Method inactivity =
                LobbyGameService.class.getDeclaredMethod("handleInactivity", Lobby.class);
        inactivity.setAccessible(true);

        for (int hand = 0; hand < 2; hand++) {
            game.startHand(lobby);
            while (lobby.getCurrentPlayerIndex() != 2) act(lobby, PlayerAction.CALL);
            fireTurnTimer(lobby, lobby.getTurnTimerGeneration(), 2); // carol times out
            if (hand == 1) {
                // Carol leaves mid-hand and dave takes her seat straight away
                lobby.getPlayersAtTable()[2] = null;
                ConnectedPlayer dave = seat(lobby, 2, "dave", 500);
                dave.setPlayerState(PlayerState.FOLDED);
            }
            checkOrCallToShowdown(lobby);
            inactivity.invoke(game, lobby);
        }

        Field f = LobbyGameService.class.getDeclaredField("consecutiveMissedHands");
        f.setAccessible(true);
        Map<String, Map<Integer, Integer>> streaks = (Map<String, Map<Integer, Integer>>) f.get(game);
        int daveId = lobby.getPlayersAtTable()[2].playerRecord.getId();
        assertEquals(0, streaks.get(lobby.getId()).get(daveId),
                "dave never missed a hand — he must not be one away from an inactivity kick");
    }

    // ─── Regression: hand-complete presentation ──────────────────────────────────

    @Test
    void foldWinClearsBetLabels() {
        Lobby lobby = lowStakesLobby();
        ConnectedPlayer alice = seat(lobby, 0, "alice", 200);
        ConnectedPlayer bob = seat(lobby, 1, "bob", 200);
        game.startHand(lobby);
        act(lobby, PlayerAction.RAISE, 10); // alice raises to 12
        act(lobby, PlayerAction.FOLD);      // bob folds

        assertEquals(GamePhase.HAND_COMPLETE, lobby.getGamePhase());
        assertEquals(0, lobby.getRoundContribution(alice));
        assertEquals(0, lobby.getRoundContribution(bob));
        assertTrue(stateBuilder.build(lobby).players().stream().allMatch(p -> p.betThisRound() == 0));
    }

    @Test
    void allInWinnerWhoDisconnectedStillAppearsInTheResult() {
        boolean departedPlayerWonOnce = false;
        for (int trial = 0; trial < 40; trial++) {
            java.util.List<GameStateDto> states = new java.util.concurrent.CopyOnWriteArrayList<>();
            SimpMessagingTemplate capture = new SimpMessagingTemplate((message, timeout) -> {
                if (message.getPayload() instanceof GameStateDto dto) states.add(dto);
                return true;
            });
            com.collegeshowdown.poker_project.services.LobbyManagerService manager =
                    org.mockito.Mockito.mock(com.collegeshowdown.poker_project.services.LobbyManagerService.class);
            org.mockito.Mockito.doAnswer(inv -> {
                inv.<Lobby>getArgument(0).getPlayersAtTable()[inv.<Integer>getArgument(1)] = null;
                return null;
            }).when(manager).leaveTable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
            LobbyGameService g = new LobbyGameService(new DealerService(), new PotManagerService(),
                    new WinnerService(), stateBuilder, capture, manager);
            g.setRunOutStepMillis(1);

            Lobby lobby = lowStakesLobby();
            ConnectedPlayer alice = seat(lobby, 0, "alice", 100);
            seat(lobby, 1, "bob", 300);
            g.startHand(lobby);
            g.processPlayerAction(lobby, 0, PlayerAction.ALL_IN, 0); // alice shoves
            assertFalse(g.handlePlayerDeparture(lobby, 0));          // ...and disconnects
            g.processPlayerAction(lobby, 1, PlayerAction.CALL, 0);   // bob calls
            awaitHandComplete(lobby);
            synchronized (lobby) { } // the run-out thread finishes concluding under this lock

            GameStateDto result = states.stream()
                    .filter(st -> st.phase().equals("HAND_COMPLETE"))
                    .findFirst().orElseThrow();
            assertEquals(lobby.getWinners().size(), result.winnerSeats().size(),
                    "every winner must be named in the hand result");
            if (lobby.getWinners().contains(alice)) departedPlayerWonOnce = true;
        }
        assertTrue(departedPlayerWonOnce, "alice should win at least one of 40 all-ins");
    }

    // ─── Helper for the common two-player setup ──────────────────────────────────

    private Lobby seatTwo(Lobby lobby, int chipsA, int chipsB) {
        seat(lobby, 0, "alice", chipsA);
        seat(lobby, 1, "bob", chipsB);
        return lobby;
    }
}
