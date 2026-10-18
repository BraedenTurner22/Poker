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

    /**
     * User IDs whose turn timer fired at least once this hand (per lobby).
     * Keyed by player, not seat: a seat can change hands mid-hand, and the
     * newcomer must not inherit the previous occupant's missed hands.
     */
    private final Map<String, Set<Integer>> timedOutThisHand = new ConcurrentHashMap<>();
    /** Number of consecutive fully-missed hands per user ID per lobby. */
    private final Map<String, Map<Integer, Integer>> consecutiveMissedHands = new ConcurrentHashMap<>();

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
            consecutiveMissedHands.computeIfAbsent(lobby.getId(), k -> new ConcurrentHashMap<>());

            // The big blind moves one player clockwise from last hand's, and
            // the small blind is the player just before it. Moving the small
            // blind instead made the old big blind post it again whenever
            // someone sat down between the blinds, and let the next player
            // skip the big blind whenever the big blind left.
            int previousBb = lobby.getBigBlindSeat();

            resetLobby(lobby);

            // Exactly two players dealt in: heads-up action order applies postflop
            lobby.setHeadsUpHand(lobby.getActivePlayersList().size() == 2);

            int sbSeat;
            int bbSeat;
            if (previousBb == -1) {
                sbSeat = getFirstValidIndex(lobby, 0);
                bbSeat = getNextValidIndex(lobby, sbSeat);
            } else {
                bbSeat = getNextValidIndex(lobby, previousBb);
                sbSeat = getPreviousValidIndex(lobby, bbSeat);
            }

            lobby.setSmallBlindSeat(sbSeat);
            lobby.setBigBlindSeat(bbSeat);

            potManagerService.postBlinds(lobby, sbSeat, bbSeat);
            dealerService.dealHoleCards(lobby);

            lobby.setGamePhase(GamePhase.PRE_FLOP);
            lobby.setCurrentBetAmount(lobby.getBigBlind());
            lobby.setLastRaiseSize(lobby.getBigBlind());
            lobby.resetPlayersActedThisRound();
            lobby.setLastAggressorIndex(bbSeat);

            // Short-stacked blinds can leave nobody with a decision (e.g. both
            // blinds all-in heads-up) — there'd be no one to give the turn to.
            if (noDecisionLeft(lobby)) {
                logger.info("Hand started in lobby {} with no decisions left — running out the board",
                        lobby.getId());
                advancePhase(lobby);
                return;
            }

            int firstToAct = getNextValidIndex(lobby, bbSeat);
            lobby.setCurrentPlayerIndex(firstToAct);

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
            ConnectedPlayer actor = playerSeat >= 0 && playerSeat < Lobby.TABLE_SIZE
                    ? lobby.getPlayersAtTable()[playerSeat] : null;
            if (timedOut != null && actor != null) timedOut.remove(actor.playerRecord.getId());
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

        // A player who already acted and since only faced an incomplete
        // (short all-in) raise may call or fold, but not re-raise.
        int owed = lobby.getCurrentBetAmount() - lobby.getRoundContribution(player);
        boolean isRaiseAttempt = action == PlayerAction.RAISE
                || (action == PlayerAction.ALL_IN && player.getActiveChips() > owed);
        if (isRaiseAttempt && lobby.getRaiseClosedSeats().contains(playerSeat)) {
            logger.warn("Illegal {} from seat {} ignored: betting not reopened by an incomplete raise",
                    action, playerSeat);
            return;
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

        if (isBettingRoundComplete(lobby) || noDecisionLeft(lobby)) {
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
                int chips = player.getActiveChips();
                // Validation guarantees raiseAmount is at least the minimum
                // unless the player can't afford a full raise — then the only
                // legal raise is all-in. long math: the amount is client-supplied.
                boolean canAffordMinRaise = chips >= callPortion + lobby.getLastRaiseSize();
                int total = canAffordMinRaise
                        ? (int) Math.min((long) callPortion + raiseAmount, chips)
                        : chips;
                int actual = player.raise(total, workingPot);
                lobby.addHandContribution(player, actual);
                lobby.addRoundContribution(player, actual);
                // The new bet is what was actually put in, not what was asked for
                applyBetIncrease(lobby, playerSeat, lobby.getRoundContribution(player));
            }

            case ALL_IN -> {
                int chips = player.getActiveChips();
                int actual = player.raise(chips, workingPot);
                lobby.addHandContribution(player, actual);
                lobby.addRoundContribution(player, actual);
                applyBetIncrease(lobby, playerSeat, lobby.getRoundContribution(player));
            }
        }
    }

    /**
     * Raises the current bet to {@code newRoundContribution} if it's higher.
     * A full raise (increment >= last raise size) sets the new minimum and
     * reopens raising for everyone; an incomplete one (a short all-in) still
     * makes everyone match it, but players who had already acted may only
     * call or fold.
     */
    private void applyBetIncrease(Lobby lobby, int playerSeat, int newRoundContribution) {
        if (newRoundContribution <= lobby.getCurrentBetAmount()) return; // a call, possibly short

        int increment = newRoundContribution - lobby.getCurrentBetAmount();
        lobby.setCurrentBetAmount(newRoundContribution);

        if (increment >= lobby.getLastRaiseSize()) {
            lobby.setLastRaiseSize(increment);
            lobby.setLastAggressorIndex(playerSeat);
            lobby.getRaiseClosedSeats().clear();
        } else {
            lobby.getRaiseClosedSeats().addAll(lobby.getPlayersActedThisRound());
        }
        // Every active player's contribution is <= the old bet, so all of
        // them now owe chips and must act again.
        lobby.resetPlayersActedThisRound();
        lobby.getPlayersActedThisRound().add(playerSeat);
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



    /**
     * True when no ACTIVE player has a decision to make: there are none, or
     * the lone one has already matched everyone else (who are all-in or
     * folded) — betting against nobody is meaningless.
     */
    private boolean noDecisionLeft(Lobby lobby) {
        List<ConnectedPlayer> active = lobby.getActivePlayersList();
        if (active.isEmpty()) return true;
        if (active.size() > 1) return false;

        ConnectedPlayer lone = active.get(0);
        int highestOther = 0;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p != lone) {
                highestOther = Math.max(highestOther, lobby.getRoundContribution(p));
            }
        }
        return lobby.getRoundContribution(lone) >= highestOther;
    }



    private void advancePhase(Lobby lobby) {
        potManagerService.recalculatePots(lobby);
        lobby.resetPlayersActedThisRound();
        lobby.resetRoundContributions();
        lobby.setCurrentBetAmount(0);
        lobby.setLastRaiseSize(lobby.getBigBlind());
        lobby.getRaiseClosedSeats().clear();

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

        // Postflop the first live seat left of the button acts first. The
        // button sits just before the small blind — except heads-up, where
        // the button IS the small blind, so the big blind acts first.
        int firstToAct = lobby.isHeadsUpHand()
                ? getNextValidIndex(lobby, lobby.getSmallBlindSeat())
                : getNextValidIndex(lobby, prevSeat(lobby.getSmallBlindSeat()));
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

        refundUncalledExcess(lobby, player);

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
        } else if (lobby.getCurrentPlayerIndex() == -1) {
            // An all-in run-out is already revealing the board — advancing
            // again would start a second, parallel run-out chain.
        } else if (isBettingRoundComplete(lobby) || noDecisionLeft(lobby)) {
            cancelTurnTimer(lobby);
            advancePhase(lobby);
        }
        return true;
        }
    }



    /**
     * A departing player's bet that nobody has called yet goes back to their
     * stack (so leaveTable cashes it out) instead of being left in the pot
     * for the players who never had to match it. The current bet drops back
     * to what the remaining players actually face.
     */
    private void refundUncalledExcess(Lobby lobby, ConnectedPlayer player) {
        int mine = lobby.getHandContributions().getOrDefault(player, 0);
        int highestOther = 0;
        for (Map.Entry<ConnectedPlayer, Integer> e : lobby.getHandContributions().entrySet()) {
            if (e.getKey() != player) highestOther = Math.max(highestOther, e.getValue());
        }
        int excess = mine - highestOther;
        if (excess <= 0) return;

        // Any excess was bet this street: on earlier streets another player
        // still in the hand matched it, or no further betting would happen.
        player.addChips(excess);
        lobby.addHandContribution(player, -excess);
        lobby.addRoundContribution(player, -excess);

        int highestRound = 0;
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null) highestRound = Math.max(highestRound, lobby.getRoundContribution(p));
        }
        lobby.setCurrentBetAmount(highestRound);
        // A raise increment can never exceed the bet it built, nor be below the big blind
        lobby.setLastRaiseSize(Math.max(lobby.getBigBlind(),
                Math.min(lobby.getLastRaiseSize(), highestRound)));
        potManagerService.recalculatePots(lobby);
        logger.info("Returned {} uncalled chips to departing player {} in lobby {}",
                excess, player.playerRecord.getId(), lobby.getId());
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
        // Every bet has been swept into the pot — clear the bet labels, as
        // advancePhase does on the showdown path
        lobby.resetRoundContributions();
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);
        cancelTurnTimer(lobby);
        cancelRunOut(lobby);
        logger.info("Hand complete in lobby {} — everyone folded to {}",
                lobby.getId(), winner.playerRecord.getName());
        // Broadcast the result while every winner is still seated — a winner
        // who disconnected while all-in is vacated just below, and the
        // winner banner can only name seated players.
        broadcastState(lobby);
        processPendingDepartures(lobby);
        scheduleNextHand(lobby);
    }



    private void concludeHand(Lobby lobby) {
        if (lobby.getGamePhase() == GamePhase.HAND_COMPLETE) return;

        winnerService.determineWinners(lobby);
        lobby.setGamePhase(GamePhase.HAND_COMPLETE);
        cancelTurnTimer(lobby);
        logger.info("Hand complete in lobby {}", lobby.getId());
        // Broadcast the result while every winner is still seated — a winner
        // who disconnected while all-in is vacated just below, and the
        // winner banner can only name seated players.
        broadcastState(lobby);
        processPendingDepartures(lobby);
        scheduleNextHand(lobby);
    }

    /** Vacates any seats that disconnected while ALL_IN, now that the hand is over. */
    private void processPendingDepartures(Lobby lobby) {
        if (lobbyManagerService == null || lobby.getPendingDepartureSeats().isEmpty()) return;
        for (int seat : new java.util.ArrayList<>(lobby.getPendingDepartureSeats())) {
            lobbyManagerService.leaveTable(lobby, seat);
        }
        lobby.getPendingDepartureSeats().clear();
        broadcastState(lobby); // show the vacated seats
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
                    if (lobby.tableCount() == 0) {
                        // Retired lobby — drop its per-lobby bookkeeping
                        timedOutThisHand.remove(lobby.getId());
                        consecutiveMissedHands.remove(lobby.getId());
                        turnTimers.remove(lobby.getId());
                        runOutTasks.remove(lobby.getId());
                    }
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
            synchronized (lobby) {
                if (lobby.getGamePhase() == GamePhase.WAITING) {
                    startHand(lobby);
                }
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
        Map<Integer, Integer> previous = consecutiveMissedHands.getOrDefault(lobbyId, Map.of());
        Map<Integer, Integer> streaks = new ConcurrentHashMap<>();
        ConnectedPlayer[] table = lobby.getPlayersAtTable();

        // Only players still seated keep a streak; anyone who left starts over
        for (ConnectedPlayer p : table) {
            if (p == null) continue;
            int userId = p.playerRecord.getId();
            streaks.put(userId, timedOut.contains(userId) ? previous.getOrDefault(userId, 0) + 1 : 0);
        }
        consecutiveMissedHands.put(lobbyId, streaks);

        for (int seat = 0; seat < Lobby.TABLE_SIZE; seat++) {
            if (table[seat] == null) continue;
            int userId = table[seat].playerRecord.getId();
            if (streaks.get(userId) < INACTIVITY_HAND_LIMIT) continue;
            streaks.put(userId, 0);
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

        // Pin the timer to this exact turn: cancel(false) can't stop a timer
        // that already fired and is blocked on the lobby lock, and it must not
        // then act on whoever's turn it is by the time it gets in.
        long generation = lobby.getTurnTimerGeneration();
        int seat = lobby.getCurrentPlayerIndex();
        ScheduledFuture<?> future = scheduler.schedule(
                logged(() -> onTurnTimeout(lobby, generation, seat)),
                TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        turnTimers.put(lobbyId, future);
    }

    private void onTurnTimeout(Lobby lobby, long generation, int seat) {
        synchronized (lobby) {
            if (lobby.getTurnTimerGeneration() != generation
                    || lobby.getCurrentPlayerIndex() != seat || seat < 0) {
                return; // stale — that turn already ended
            }
            String lobbyId = lobby.getId();
            ConnectedPlayer p = lobby.getPlayersAtTable()[seat];
            // Check when free; only fold if there's an outstanding bet to match
            boolean canCheck = p != null
                    && lobby.getRoundContribution(p) >= lobby.getCurrentBetAmount();
            PlayerAction autoAction = canCheck ? PlayerAction.CHECK : PlayerAction.FOLD;
            logger.info("Turn timer expired in lobby {} — auto {} for seat {}",
                    lobbyId, autoAction, seat);
            if (p != null) {
                timedOutThisHand.computeIfAbsent(lobbyId, k -> ConcurrentHashMap.newKeySet())
                        .add(p.playerRecord.getId());
            }
            processPlayerActionCore(lobby, seat, autoAction, 0);
        }
    }



    private void cancelTurnTimer(Lobby lobby) {
        lobby.bumpTurnTimerGeneration();
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
        lobby.getRaiseClosedSeats().clear();

        // Every seated player with chips comes back ACTIVE for the new hand —
        // a fold or all-in only lasts for the hand it happened in. A busted
        // player (still ALL_IN from losing last hand) sits out as FOLDED until
        // they rebuy; left ALL_IN they'd count as a live player in a hand
        // they were never dealt into, and a mid-hand rebuy would flip them
        // straight to ACTIVE.
        for (ConnectedPlayer player : lobby.getPlayersAtTable()) {
            if (player == null) continue;
            player.getCards().clear();
            player.getBestCards().clear();
            player.setHandRank(null);
            player.setPlayerState(player.getActiveChips() > 0
                    ? PlayerState.ACTIVE : PlayerState.FOLDED);
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
            // Any funded player can take the small blind — a short stack just
            // posts all-in. Requiring a full small blind froze the lobby when
            // every stack was shorter than it.
            if (p != null && p.getPlayerState() == PlayerState.ACTIVE) {
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



    /** The nearest ACTIVE seat counter-clockwise from {@code currentIndex}. */
    private int getPreviousValidIndex(Lobby lobby, int currentIndex) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        for (int i = 1; i <= Lobby.TABLE_SIZE; i++) {
            int candidate = (currentIndex - i + Lobby.TABLE_SIZE) % Lobby.TABLE_SIZE;
            ConnectedPlayer p = table[candidate];
            if (p != null && p.getPlayerState() == PlayerState.ACTIVE) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No previous valid player in lobby " + lobby.getId());
    }
}
