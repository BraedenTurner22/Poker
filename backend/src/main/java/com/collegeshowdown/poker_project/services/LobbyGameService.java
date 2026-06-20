package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerAction;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.dto.GameStateDto;
import com.collegeshowdown.poker_project.dto.PrivateStateDto;
import com.collegeshowdown.poker_project.service.GameStateBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class LobbyGameService {
    private final static Logger logger = LoggerFactory.getLogger(LobbyGameService.class);

    private final DealerService dealerService;
    private final PotManagerService potManagerService;
    private final WinnerService winnerService;
    private final GameStateBuilder gameStateBuilder;
    private final SimpMessagingTemplate messagingTemplate;
    private final LobbyManagerService lobbyManagerService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final Map<String, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> runOutTasks = new ConcurrentHashMap<>();

    /** Seats that had their turn timer fire at least once this hand (per lobby). */
    private final Map<String, Set<Integer>> timedOutThisHand = new ConcurrentHashMap<>();
    /** Number of consecutive fully-missed hands per seat per lobby. */
    private final Map<String, int[]> consecutiveMissedHands = new ConcurrentHashMap<>();

    private static final int TURN_TIMEOUT_SECONDS = 30;
    private static final int NEXT_HAND_DELAY_SECONDS = 5;
    private static final int INACTIVITY_HAND_LIMIT = 3;

    /** Pause between each revealed street during an all-in run-out (tests shrink this). */
    private long runOutStepMillis = 1500;

    public void setRunOutStepMillis(long millis) {
        this.runOutStepMillis = millis;
    }

    @Autowired
    public LobbyGameService(DealerService dealerService,
            PotManagerService potManagerService,
            WinnerService winnerService,
            GameStateBuilder gameStateBuilder,
            @Lazy SimpMessagingTemplate messagingTemplate,
            @Lazy LobbyManagerService lobbyManagerService) {
        this.dealerService = dealerService;
        this.potManagerService = potManagerService;
        this.winnerService = winnerService;
        this.gameStateBuilder = gameStateBuilder;
        this.messagingTemplate = messagingTemplate;
        this.lobbyManagerService = lobbyManagerService;
    }



    // ─── Public API ───────────────────────────────────────────────────────────────

    /**
     * Begins a new hand. Non-blocking — sets up state and starts the first
     * player's turn timer, then broadcasts the initial state.
     */
    public void startHand(Lobby lobby) {
        synchronized (lobby) {
            // Idempotency guard: a hand may only legitimately start when the
            // table is idle (WAITING) or just finished the previous hand
            // (HAND_COMPLETE) — never mid-hand. Without this, two overlapping
            // triggers (e.g. a rebuy and a new join landing close together)
            // could both reach here and re-deal on top of a hand in progress.
            if (lobby.getGamePhase() != GamePhase.WAITING
                    && lobby.getGamePhase() != GamePhase.HAND_COMPLETE) {
                logger.warn("Ignoring startHand for lobby {} — hand already in progress (phase={})",
                        lobby.getId(), lobby.getGamePhase());
                return;
            }

            if (countPlayersWithChips(lobby) < 2) {
                logger.warn("Cannot start hand with fewer than 2 funded players");
                resetLobby(lobby);
                lobby.setGamePhase(GamePhase.WAITING);
                broadcastState(lobby);
                return;
            }

            // Fresh per-hand inactivity tracking
            timedOutThisHand.put(lobby.getId(), ConcurrentHashMap.newKeySet());
            consecutiveMissedHands.computeIfAbsent(lobby.getId(), k -> new int[Lobby.TABLE_SIZE]);

            // The small blind moves one occupied seat clockwise from last hand's
            int previousSb = lobby.getSmallBlindSeat();

            resetLobby(lobby);

            int sbSeat = previousSb == -1
                    ? getFirstValidIndex(lobby, 0)
                    : getNextValidIndex(lobby, previousSb);
            int bbSeat = getNextValidIndex(lobby, sbSeat);

            lobby.setSmallBlindSeat(sbSeat);
            lobby.setBigBlindSeat(bbSeat);

            potManagerService.postBlinds(lobby, sbSeat, bbSeat);
            dealerService.dealHoleCards(lobby);

            lobby.setGamePhase(GamePhase.PRE_FLOP);
            lobby.setCurrentBetAmount(lobby.getBigBlind());
            lobby.setLastRaiseSize(lobby.getBigBlind());
            lobby.resetPlayersActedThisRound();

            int firstToAct = getNextValidIndex(lobby, bbSeat);
            lobby.setCurrentPlayerIndex(firstToAct);
            lobby.setLastAggressorIndex(bbSeat);

            logger.info("Hand started in lobby {} — phase={}, first to act: seat {}",
                    lobby.getId(), lobby.getGamePhase(), firstToAct);

            startTurnTimer(lobby);
            broadcastState(lobby);
        }
    }



    /**
     * Processes a player action. Called by the WebSocket controller for voluntary actions.
     * Resets the inactivity streak for this player.
     */
    public void processPlayerAction(Lobby lobby, int playerSeat,
            PlayerAction action, int raiseAmount) {
        synchronized (lobby) {
            // Voluntary action — clear any timed-out mark for this hand
            // (Collections.emptySet() is immutable; use a null-check instead of getOrDefault)
            Set<Integer> timedOut = timedOutThisHand.get(lobby.getId());
            if (timedOut != null) timedOut.remove(playerSeat);
            processPlayerActionCore(lobby, playerSeat, action, raiseAmount);
        }
    }

    /** Internal path (timer, departure) — does NOT reset inactivity. */
    private void processPlayerActionCore(Lobby lobby, int playerSeat,
            PlayerAction action, int raiseAmount) {
        synchronized (lobby) {

        if (lobby.getGamePhase() == GamePhase.WAITING
                || lobby.getGamePhase() == GamePhase.HAND_COMPLETE) {
            logger.warn("Action received outside of an active hand (phase={})", lobby.getGamePhase());
            return;
        }

        if (playerSeat < 0 || playerSeat >= Lobby.TABLE_SIZE
                || lobby.getPlayersAtTable()[playerSeat] == null) {
            logger.warn("Action from invalid seat {} ignored", playerSeat);
            return;
        }

        if (lobby.getCurrentPlayerIndex() != playerSeat) {
            logger.warn("Out-of-turn action ignored: expected seat {}, got seat {}",
                    lobby.getCurrentPlayerIndex(), playerSeat);
            return;
        }

        ConnectedPlayer player = lobby.getPlayersAtTable()[playerSeat];
        Pot workingPot = lobby.getCurrentPot();

        // Reject an illegal CHECK while a bet is outstanding (leave timer running)
        if (action == PlayerAction.CHECK
                && lobby.getRoundContribution(player) < lobby.getCurrentBetAmount()) {
            logger.warn("Illegal CHECK from seat {} ignored (bet outstanding)", playerSeat);
            return;
        }

        // Reject an illegal RAISE: must be at least the minimum re-raise size
        // unless the player can't afford a full one, in which case ALL_IN is
        // the correct action and a short RAISE is allowed to become one.
        if (action == PlayerAction.RAISE) {
            if (raiseAmount <= 0) {
                logger.warn("Illegal RAISE from seat {} ignored: amount must be positive", playerSeat);
                return;
            }
            int callPortion = lobby.getCurrentBetAmount() - lobby.getRoundContribution(player);
            boolean canAffordMinRaise = player.getActiveChips() >= callPortion + lobby.getLastRaiseSize();
            if (raiseAmount < lobby.getLastRaiseSize() && canAffordMinRaise) {
                logger.warn("Illegal RAISE from seat {} ignored: {} is below the minimum raise of {}",
                        playerSeat, raiseAmount, lobby.getLastRaiseSize());
                return;
            }
        }

        cancelTurnTimer(lobby);

        applyAction(lobby, player, playerSeat, action, raiseAmount, workingPot);
        lobby.getPlayersActedThisRound().add(playerSeat);

        logger.info("Lobby {} seat {} — action={} raiseAmt={}",
                lobby.getId(), playerSeat, action, raiseAmount);

        // Everyone else folded — lone player takes all pots, no showdown
        if (countNonFolded(lobby) == 1) {
            potManagerService.recalculatePots(lobby);
            concludeFoldWin(lobby);
            return;
        }

        if (isBettingRoundComplete(lobby)) {
            advancePhase(lobby);
        } else {
            lobby.setCurrentPlayerIndex(getNextValidIndex(lobby, playerSeat));
            startTurnTimer(lobby);
            broadcastState(lobby);
        }
        }
    }



    // ─── Private game flow ────────────────────────────────────────────────────────

    private void applyAction(Lobby lobby, ConnectedPlayer player, int playerSeat,
            PlayerAction action, int raiseAmount, Pot workingPot) {
        switch (action) {
            case FOLD -> player.foldCards(lobby.getAllActivePots());

            case CHECK -> {}

            case CALL -> {
                int already = lobby.getRoundContribution(player);
                int toCall = Math.min(lobby.getCurrentBetAmount() - already, player.getActiveChips());
                if (toCall > 0) {
                    int actual = player.call(toCall, workingPot);
                    lobby.addHandContribution(player, actual);
                    lobby.addRoundContribution(player, actual);
                }
            }

            case RAISE -> {
                int already = lobby.getRoundContribution(player);
                int callPortion = lobby.getCurrentBetAmount() - already;
                int total = Math.min(callPortion + raiseAmount, player.getActiveChips());
                int actual = player.raise(total, workingPot);
                lobby.addHandContribution(player, actual);
                lobby.addRoundContribution(player, actual);
                lobby.setCurrentBetAmount(lobby.getCurrentBetAmount() + raiseAmount);
                lobby.setLastRaiseSize(raiseAmount);
                lobby.setLastAggressorIndex(playerSeat);
                lobby.resetPlayersActedThisRound();
                lobby.getPlayersActedThisRound().add(playerSeat);
            }

            case ALL_IN -> {
                int chips = player.getActiveChips();
                int actual = player.raise(chips, workingPot);
                lobby.addHandContribution(player, actual);
                lobby.addRoundContribution(player, actual);
                int totalRoundContrib = lobby.getRoundContribution(player);
                if (totalRoundContrib > lobby.getCurrentBetAmount()) {
                    int increment = totalRoundContrib - lobby.getCurrentBetAmount();
                    lobby.setCurrentBetAmount(totalRoundContrib);

                    // Every active player's contribution this round is always
                    // <= the old currentBetAmount (you can only ever call up to
                    // it, never beyond), so anyone who hasn't already matched
                    // this new, higher amount needs to act regardless of how
                    // big the increment was — there's no active seat that could
                    // be correctly skipped here. The one thing that *should*
                    // depend on whether this was a full raise is the minimum
                    // for the *next* real raise: an incomplete (short all-in)
                    // raise doesn't reset that bar.
                    //
                    // (Simplification: formally, an incomplete raise also
                    // shouldn't grant players who already called the *new*
                    // amount the right to re-raise past the old minimum — that
                    // would need per-seat raise-eligibility tracking this
                    // engine doesn't have. Low-impact edge case, not modeled.)
                    if (increment >= lobby.getLastRaiseSize()) {
                        lobby.setLastRaiseSize(increment);
                        lobby.setLastAggressorIndex(playerSeat);
                    }
                    lobby.resetPlayersActedThisRound();
                    lobby.getPlayersActedThisRound().add(playerSeat);
                }
            }
        }
    }



    /**
     * The round is complete when every ACTIVE player has acted since the last
     * aggression (a raise resets playersActedThisRound to just the raiser).
     */
    private boolean isBettingRoundComplete(Lobby lobby) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            ConnectedPlayer p = table[i];
            if (p != null && p.getPlayerState() == PlayerState.ACTIVE
                    && !lobby.getPlayersActedThisRound().contains(i)) {
                return false;
            }
        }
        return true;
    }



    private void advancePhase(Lobby lobby) {
        potManagerService.recalculatePots(lobby);
        lobby.resetPlayersActedThisRound();
        lobby.resetRoundContributions();
        lobby.setCurrentBetAmount(0);
        lobby.setLastRaiseSize(lobby.getBigBlind());

        // Fewer than 2 players can still bet (rest are all-in): no more betting
        // rounds — reveal the remaining streets one by one, then showdown.
        if (lobby.getActivePlayersList().size() < 2) {
            beginStagedRunOut(lobby);
            return;
        }

        switch (lobby.getGamePhase()) {
            case PRE_FLOP -> { dealerService.dealFlop(lobby);  lobby.setGamePhase(GamePhase.FLOP); }
            case FLOP     -> { dealerService.dealTurn(lobby);  lobby.setGamePhase(GamePhase.TURN); }
            case TURN     -> { dealerService.dealRiver(lobby); lobby.setGamePhase(GamePhase.RIVER); }
            case RIVER    -> { lobby.setGamePhase(GamePhase.SHOWDOWN); concludeHand(lobby); return; }
            // Never start a betting round from a non-betting phase — if a
            // previous conclude attempt failed, retry it instead of looping.
            default -> { concludeHand(lobby); return; }
        }

        int firstToAct = getNextValidIndex(lobby, prevSeat(lobby.getSmallBlindSeat()));
        lobby.setCurrentPlayerIndex(firstToAct);
        lobby.setLastAggressorIndex(firstToAct);

        // Fresh working pot for this street
        lobby.getAllActivePots().add(
                new com.collegeshowdown.poker_project.domain.lobby.Pot(lobby.getActivePlayersList()));

        logger.info("Lobby {} advanced to {}", lobby.getId(), lobby.getGamePhase());
        startTurnTimer(lobby);
        broadcastState(lobby);
    }



    /**
     * Starts the dramatic all-in run-out: no one can act anymore, so the
     * remaining streets are revealed one at a time with a pause between each,
     * then the hand concludes.
     */
    private void beginStagedRunOut(Lobby lobby) {
        lobby.setCurrentPlayerIndex(-1); // nobody's turn during the run-out
        lobby.setTurnDeadlineEpochMs(0);
        broadcastState(lobby);
        scheduleRunOutStep(lobby);
    }

    private void scheduleRunOutStep(Lobby lobby) {
        runOutTasks.put(lobby.getId(), scheduler.schedule(
                logged(() -> runOutStep(lobby)),
                runOutStepMillis, TimeUnit.MILLISECONDS));
    }

    private void runOutStep(Lobby lobby) {
        synchronized (lobby) {
        // The hand may have ended some other way (e.g. everyone else left)
        if (lobby.getGamePhase() == GamePhase.WAITING
                || lobby.getGamePhase() == GamePhase.HAND_COMPLETE) return;

        int boardSize = lobby.getBoard().size();
        if (boardSize < 5) {
            switch (boardSize) {
                case 0 -> { dealerService.dealFlop(lobby);  lobby.setGamePhase(GamePhase.FLOP); }
                case 3 -> { dealerService.dealTurn(lobby);  lobby.setGamePhase(GamePhase.TURN); }
                case 4 -> { dealerService.dealRiver(lobby); lobby.setGamePhase(GamePhase.RIVER); }
                default -> { }
            }
            broadcastState(lobby);
            scheduleRunOutStep(lobby);
        } else {
            lobby.setGamePhase(GamePhase.SHOWDOWN);
            concludeHand(lobby);
        }
        }
    }

    private void cancelRunOut(Lobby lobby) {
        ScheduledFuture<?> task = runOutTasks.remove(lobby.getId());
        if (task != null) task.cancel(false);
    }



    /**
     * Called when a player leaves or disconnects mid-game. Folds them out of
     * the current hand and immediately concludes the hand if they were the
     * last opponent — the remaining player collects whatever is in the middle.
     *
     * Returns true if the caller should go ahead and vacate the seat now,
     * false if the seat must stay put for now. An ALL_IN player has no more
     * decisions to make but is still entitled to whatever they're owed at
     * showdown — folding them here would strip their equity from every pot.
     * Their seat is instead marked pending and vacated once the hand
     * concludes (see concludeHand/concludeFoldWin).
     */
    public boolean handlePlayerDeparture(Lobby lobby, int seat) {
        synchronized (lobby) {
        boolean handInProgress = lobby.getGamePhase() != GamePhase.WAITING
                && lobby.getGamePhase() != GamePhase.HAND_COMPLETE;
        if (!handInProgress) return true;

        ConnectedPlayer player = lobby.getPlayersAtTable()[seat];
        if (player == null || player.getPlayerState() == PlayerState.FOLDED) return true;

        if (player.getPlayerState() == PlayerState.ALL_IN) {
            lobby.getPendingDepartureSeats().add(seat);
            logger.info("Seat {} in lobby {} disconnected while ALL_IN — deferring seat removal until showdown",
                    seat, lobby.getId());
            return false;
        }

        if (lobby.getCurrentPlayerIndex() == seat) {
            // Folding through the normal path advances the turn / hand correctly
            processPlayerActionCore(lobby, seat, PlayerAction.FOLD, 0);
            return true;
        }

        player.foldCards(lobby.getAllActivePots());
        lobby.getPlayersActedThisRound().add(seat);

        if (countNonFolded(lobby) == 1) {
            potManagerService.recalculatePots(lobby);
            concludeFoldWin(lobby);
        } else if (isBettingRoundComplete(lobby)) {
            cancelTurnTimer(lobby);
            advancePhase(lobby);
        }
        return true;
        }
    }



    /** Lone non-folded player takes every pot without a showdown. */
    private void concludeFoldWin(Lobby lobby) {
        if (lobby.getGamePhase() == GamePhase.HAND_COMPLETE) return;

        ConnectedPlayer winner = null;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.getPlayerState() != PlayerState.FOLDED) {
                winner = p;
                break;
            }
        }
        if (winner == null) {
            logger.error("Fold-win with no remaining player in lobby {}", lobby.getId());
            return;
        }

        for (Pot pot : lobby.getAllActivePots()) {
            winnerService.distributePot(pot, List.of(winner));
        }
        // Must be mutable — resetLobby clears this list next hand
        lobby.setWinners(new java.util.ArrayList<>(List.of(winner)));
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);
        cancelTurnTimer(lobby);
        cancelRunOut(lobby);
        logger.info("Hand complete in lobby {} — everyone folded to {}",
                lobby.getId(), winner.playerRecord.getName());
        processPendingDepartures(lobby);
        broadcastState(lobby);
        scheduleNextHand(lobby);
    }



    private void concludeHand(Lobby lobby) {
        if (lobby.getGamePhase() == GamePhase.HAND_COMPLETE) return;

        winnerService.determineWinners(lobby);
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);
        cancelTurnTimer(lobby);
        logger.info("Hand complete in lobby {}", lobby.getId());
        processPendingDepartures(lobby);
        broadcastState(lobby);
        scheduleNextHand(lobby);
    }

    /** Vacates any seats that disconnected while ALL_IN, now that the hand is over. */
    private void processPendingDepartures(Lobby lobby) {
        if (lobbyManagerService == null) return;
        for (int seat : new java.util.ArrayList<>(lobby.getPendingDepartureSeats())) {
            lobbyManagerService.leaveTable(lobby, seat);
        }
        lobby.getPendingDepartureSeats().clear();
    }



    private void scheduleNextHand(Lobby lobby) {
        scheduler.schedule(logged(() -> {
            synchronized (lobby) {
                handleInactivity(lobby);
                if (lobby.tableCount() >= 2) {
                    startHand(lobby);
                } else {
                    resetLobby(lobby);
                    lobby.setGamePhase(GamePhase.WAITING);
                    broadcastState(lobby);
                }
            }
        }), NEXT_HAND_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Called after a rebuy to start the next hand if the table is ready.
     * Runs on the scheduler thread so the HTTP request thread is not blocked
     * by game-state mutations.
     */
    public void scheduleHandAfterRebuy(Lobby lobby) {
        scheduler.schedule(logged(() -> {
            if (lobby.getGamePhase() == GamePhase.WAITING) {
                startHand(lobby);
            }
        }), 500, TimeUnit.MILLISECONDS);
    }



    /**
     * After each hand, increments each player's consecutive-missed-hand counter
     * if their turn timer fired this hand, then kicks anyone who hit the limit.
     */
    private void handleInactivity(Lobby lobby) {
        synchronized (lobby) {
        String lobbyId = lobby.getId();
        Set<Integer> timedOut = timedOutThisHand.getOrDefault(lobbyId, Collections.emptySet());
        int[] streaks = consecutiveMissedHands.computeIfAbsent(lobbyId, k -> new int[Lobby.TABLE_SIZE]);
        ConnectedPlayer[] table = lobby.getPlayersAtTable();

        for (int seat = 0; seat < Lobby.TABLE_SIZE; seat++) {
            if (table[seat] == null) { streaks[seat] = 0; continue; }
            if (timedOut.contains(seat)) {
                streaks[seat]++;
            } else {
                streaks[seat] = 0;
            }
        }

        for (int seat = 0; seat < Lobby.TABLE_SIZE; seat++) {
            if (streaks[seat] < INACTIVITY_HAND_LIMIT || table[seat] == null) continue;
            int userId = table[seat].playerRecord.getId();
            streaks[seat] = 0;
            logger.info("Removing seat {} (userId {}) from lobby {} due to inactivity",
                    seat, userId, lobbyId);
            messagingTemplate.convertAndSend(
                    "/topic/game/" + lobbyId + "/kick/" + userId,
                    Map.of("reason", "inactivity"));
            if (lobbyManagerService != null) lobbyManagerService.leaveTable(lobby, seat);
        }
        }
    }



    /**
     * ScheduledExecutorService swallows exceptions silently, which can freeze a
     * lobby with no trace. Every scheduled task must be wrapped in this.
     */
    private Runnable logged(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("Scheduled game task failed", e);
            }
        };
    }



    // ─── Timer ────────────────────────────────────────────────────────────────────

    private void startTurnTimer(Lobby lobby) {
        String lobbyId = lobby.getId();
        cancelTurnTimer(lobby);

        long deadline = System.currentTimeMillis() + (TURN_TIMEOUT_SECONDS * 1000L);
        lobby.setTurnDeadlineEpochMs(deadline);

        ScheduledFuture<?> future = scheduler.schedule(logged(() -> {
            synchronized (lobby) {
                int seat = lobby.getCurrentPlayerIndex();
                ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
                // Check when free; only fold if there's an outstanding bet to match
                boolean canCheck = p != null
                        && lobby.getRoundContribution(p) >= lobby.getCurrentBetAmount();
                PlayerAction autoAction = canCheck ? PlayerAction.CHECK : PlayerAction.FOLD;
                logger.info("Turn timer expired in lobby {} — auto {} for seat {}",
                        lobbyId, autoAction, seat);
                timedOutThisHand.computeIfAbsent(lobbyId, k -> ConcurrentHashMap.newKeySet()).add(seat);
                processPlayerActionCore(lobby, seat, autoAction, 0);
            }
        }), TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        turnTimers.put(lobbyId, future);
    }



    private void cancelTurnTimer(Lobby lobby) {
        lobby.setTurnDeadlineEpochMs(0);
        ScheduledFuture<?> existing = turnTimers.remove(lobby.getId());
        if (existing != null) existing.cancel(false);
    }



    // ─── Broadcasting ─────────────────────────────────────────────────────────────

    /**
     * Sends the public game state to every player in the lobby, then sends each
     * active player their private state (hole cards) on their personal topic.
     */
    public void broadcastState(Lobby lobby) {
        synchronized (lobby) {
        GameStateDto state = gameStateBuilder.build(lobby);
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobby.getId() + "/state", state);

        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        for (ConnectedPlayer player : table) {
            if (player == null || player.getCards().isEmpty()) continue;
            PrivateStateDto priv = gameStateBuilder.buildPrivate(player);
            messagingTemplate.convertAndSend(
                    "/topic/game/" + lobby.getId() + "/private/" + player.playerRecord.getId(),
                    priv);
        }
        }
    }



    // ─── Lobby reset ──────────────────────────────────────────────────────────────

    public void resetLobby(Lobby lobby) {
        synchronized (lobby) {
        logger.info("Resetting game state for lobby {}", lobby.getId());
        cancelRunOut(lobby);
        lobby.resetDeck();
        lobby.getBoard().clear();
        lobby.setWinners(new java.util.ArrayList<>()); // never clear() — may be immutable
        lobby.getAllActivePots().clear();
        lobby.resetPlayersActedThisRound();
        lobby.resetHandContributions();
        lobby.resetRoundContributions();
        lobby.setSmallBlindSeat(-1);
        lobby.setBigBlindSeat(-1);
        lobby.setTurnDeadlineEpochMs(0);

        // Every seated player with chips comes back ACTIVE for the new hand —
        // a fold or all-in only lasts for the hand it happened in.
        for (ConnectedPlayer player : lobby.getPlayersAtTable()) {
            if (player == null) continue;
            player.getCards().clear();
            player.getBestCards().clear();
            player.setHandRank(null);
            if (player.getActiveChips() > 0) {
                player.setPlayerState(PlayerState.ACTIVE);
            }
        }
        }
    }



    // ─── Player counting helpers ─────────────────────────────────────────────────

    private int countNonFolded(Lobby lobby) {
        int count = 0;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.getPlayerState() != PlayerState.FOLDED) count++;
        }
        return count;
    }

    private int countPlayersWithChips(Lobby lobby) {
        int count = 0;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.getActiveChips() > 0) count++;
        }
        return count;
    }

    private static int prevSeat(int seat) {
        return (seat + Lobby.TABLE_SIZE - 1) % Lobby.TABLE_SIZE;
    }



    // ─── Seat helpers ─────────────────────────────────────────────────────────────

    public int getFirstValidIndex(Lobby lobby, int startIndex) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        int idx = Math.max(startIndex, 0) % Lobby.TABLE_SIZE;

        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            int candidate = (idx + i) % Lobby.TABLE_SIZE;
            ConnectedPlayer p = table[candidate];
            if (p != null && p.getActiveChips() >= lobby.getSmallBlind()) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No valid player found in lobby " + lobby.getId());
    }



    public int getNextValidIndex(Lobby lobby, int currentIndex) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        int idx = (currentIndex + 1) % Lobby.TABLE_SIZE;

        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            int candidate = (idx + i) % Lobby.TABLE_SIZE;
            ConnectedPlayer p = table[candidate];
            // ACTIVE players always get to act, regardless of stack size
            if (p != null && p.getPlayerState() == PlayerState.ACTIVE) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No next valid player in lobby " + lobby.getId());
    }
}
