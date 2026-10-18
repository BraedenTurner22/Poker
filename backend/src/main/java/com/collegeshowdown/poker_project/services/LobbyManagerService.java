package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.lobby.GamePhase;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.LobbyType;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LobbyManagerService {

    private final static Logger logger = LoggerFactory.getLogger(LobbyManagerService.class);

    /** Table maximum buy-in / stack size, in big blinds. */
    private static final int MAX_BUY_IN_BIG_BLINDS = 100;

    private final LobbyGameService lobbyGameService;
    private final PlayerRepository playerRepository;

    /** key: "GLOBAL:LOW", "GLOBAL:MEDIUM", "GLOBAL:HIGH", "SCHOOL:<domain>:LOW", etc. */
    private final Map<String, List<Lobby>> lobbies = new ConcurrentHashMap<>();

    /** Fast lookup: lobbyId → Lobby */
    private final Map<String, Lobby> lobbyById = new ConcurrentHashMap<>();

    /**
     * Guards the {@code lobbies}/{@code lobbyById} maps — i.e. which lobbies
     * exist and pool membership. Never acquired *before* a per-lobby lock
     * (synchronized(lobby)) on any path; it is only ever taken on its own, or
     * while already holding a lobby lock (leaveTable's empty-lobby removal,
     * which callers such as concludeHand already reach with the lobby lock
     * held). A single consistent lobby → registry order can't deadlock.
     */
    private final Object registryLock = new Object();

    /**
     * Per-player locks guarding {@link #joinLobby}. The "am I already seated?"
     * scan (under registryLock) and the actual seat write (under the chosen
     * lobby's own lock) are two separate critical sections with a gap between
     * them — without this, two concurrent joinLobby calls for the same
     * account (e.g. a slow/cold-starting first request plus a frustrated
     * retry) can both pass the scan before either has written a seat,
     * seating the same account twice. Keyed by player ID so unrelated
     * players' joins are unaffected; never held at the same time as
     * registryLock or a per-lobby lock from any other code path, so it can't
     * introduce a new deadlock ordering.
     */
    private final Map<Integer, Object> joinLocksByPlayerId = new ConcurrentHashMap<>();

    /**
     * playerId → total chips moved from the bank to their current seat
     * (initial buy-in plus every rebuy). Profit stats are settled once, at
     * cash-out, as (chips cashed out − chips bought in) — so sitting down
     * isn't recorded as a loss on the leaderboard for the whole session, and
     * a session spanning the monthly reset doesn't credit the new month with
     * the full cash-out. Written only under the seat's lobby lock.
     */
    private final Map<Integer, Integer> sessionBuyInsByPlayerId = new ConcurrentHashMap<>();

    @Autowired
    public LobbyManagerService(LobbyGameService lobbyGameService,
            PlayerRepository playerRepository) {
        this.lobbyGameService = lobbyGameService;
        this.playerRepository = playerRepository;
    }

    /** A join or rebuy request that breaks a table rule; the message is user-facing. */
    public static class SeatingException extends RuntimeException {
        public SeatingException(String message) { super(message); }
    }

    public static int maxBuyIn(int bigBlind) {
        return MAX_BUY_IN_BIG_BLINDS * bigBlind;
    }

    /**
     * Finds an existing lobby with an open seat that matches the given criteria,
     * or creates a new one. Places the player in a seat and starts the hand if
     * the table reaches 2+ players. If the account is already seated anywhere,
     * returns that seat instead (no new buy-in).
     *
     * Returns the assigned [lobby, seatIndex] pair.
     *
     * @throws SeatingException if the bank can't cover the stakes' minimum
     *         buy-in or the requested buy-in is below it
     */
    public LobbyAssignment joinLobby(PlayerRecord record,
            StakesLevel stakes, boolean schoolLobby, int requestedBuyIn) {
        Object joinLock = joinLocksByPlayerId.computeIfAbsent(record.getId(), _ -> new Object());
        synchronized (joinLock) {
            return joinLobbyLocked(record, stakes, schoolLobby, requestedBuyIn);
        }
    }

    private LobbyAssignment joinLobbyLocked(PlayerRecord record,
            StakesLevel stakes, boolean schoolLobby, int requestedBuyIn) {

        // If this account is already seated anywhere, reconnect them to that
        // seat instead of seating them twice. Checked before the bank checks
        // below — a player who bought in with their whole bank must still be
        // able to get back to their seat.
        LobbyAssignment existing = findExistingSeat(record.getId());
        if (existing != null) return existing;

        int bank = record.getTotalChips();
        if (bank < stakes.minimumBuyIn) {
            throw new SeatingException("You need at least " + stakes.minimumBuyIn
                    + " chips to join a " + stakes.name() + " table. "
                    + "You have " + bank + ".");
        }
        if (requestedBuyIn > 0 && requestedBuyIn < stakes.minimumBuyIn) {
            throw new SeatingException("Buy-in must be at least " + stakes.minimumBuyIn
                    + " for a " + stakes.name() + " table.");
        }

        int maxBuyIn = maxBuyIn(stakes.bigBlind);
        int buyIn = requestedBuyIn > 0
                ? Math.min(requestedBuyIn, Math.min(bank, maxBuyIn))
                : Math.min(bank, maxBuyIn);

        // Buy-in moves chips from the bank to the table; they return via
        // leaveTable's cash-out. Withdrawn before a lobby is picked so a
        // failure here can't leave a freshly created, empty lobby behind.
        if (playerRepository.withdrawFromBank(record.getId(), buyIn) == 0) {
            throw new SeatingException("Insufficient bank balance for a " + buyIn + "-chip buy-in.");
        }

        try {
            return seatAfterWithdrawal(record, stakes, schoolLobby, buyIn);
        } catch (RuntimeException e) {
            // The buy-in has left the bank. If the failure came before the
            // seat was written, nothing will ever cash it out — return it.
            // (Once seated, the chips are on the table and leaveTable owns them.)
            if (findExistingSeat(record.getId()) == null) {
                logger.error("Seating player {} failed after the buy-in was withdrawn — refunding {}",
                        record.getId(), buyIn, e);
                sessionBuyInsByPlayerId.remove(record.getId());
                playerRepository.depositToBank(record.getId(), buyIn, 0);
            }
            throw e;
        }
    }

    private LobbyAssignment seatAfterWithdrawal(PlayerRecord record,
            StakesLevel stakes, boolean schoolLobby, int buyIn) {
        // Retries if a concurrent join wins the race for the lobby's last
        // open seat (or the lobby is retired as empty) between the registry
        // pick and the per-lobby seating step.
        while (true) {
            Lobby lobby;
            synchronized (registryLock) {
                String key = buildKey(stakes, schoolLobby ? record.getCollegeDomain() : null);
                List<Lobby> pool = lobbies.computeIfAbsent(key, _ -> new ArrayList<>());

                // Find a lobby with at least one open seat
                lobby = pool.stream()
                        .filter(l -> l.tableCount() < Lobby.TABLE_SIZE)
                        .findFirst()
                        .orElse(null);

                if (lobby == null) {
                    lobby = createLobby(stakes, schoolLobby, record.getCollegeDomain());
                    pool.add(lobby);
                    lobbyById.put(lobby.getId(), lobby);
                    logger.info("Created new lobby {} ({})", lobby.getId(), key);
                }
            }

            synchronized (lobby) {
                // leaveTable may have retired this lobby (last player left)
                // after we picked it — seating into it would strand the
                // player (and their buy-in) at a table nobody can look up.
                if (lobbyById.get(lobby.getId()) != lobby) continue;

                int seat = findOpenSeat(lobby);
                if (seat == -1) {
                    // Lost the race for the last seat — pick again.
                    continue;
                }

                ConnectedPlayer player = new ConnectedPlayer(record);
                player.buyIn(buyIn);
                sessionBuyInsByPlayerId.put(record.getId(), buyIn);

                // Joining mid-hand: sit out (treated as folded) until the next hand —
                // resetLobby flips every funded player back to ACTIVE when it starts.
                if (lobby.getGamePhase() != GamePhase.WAITING) {
                    player.setPlayerState(PlayerState.FOLDED);
                }
                lobby.getPlayersAtTable()[seat] = player;

                logger.info("Player {} seated at lobby {} seat {} with {} chips",
                        record.getName(), lobby.getId(), seat, buyIn);

                // A waiting table springs to life as soon as it has 2+ players
                if (lobby.getGamePhase() == GamePhase.WAITING && lobby.tableCount() >= 2) {
                    lobbyGameService.startHand(lobby);
                }

                return new LobbyAssignment(lobby, seat, buyIn);
            }
        }
    }

    /** Returns the player's current seat anywhere, or null if they aren't seated. */
    private LobbyAssignment findExistingSeat(int userId) {
        while (true) {
            Lobby found = null;
            synchronized (registryLock) {
                for (Lobby l : lobbyById.values()) {
                    if (getSeatForPlayer(l, userId) != -1) {
                        found = l;
                        break;
                    }
                }
            }
            if (found == null) return null;

            // Re-read under the lobby's own lock (never nested inside
            // registryLock — see its javadoc) — the seat may have been
            // vacated in between, in which case scan again.
            synchronized (found) {
                int seat = getSeatForPlayer(found, userId);
                if (seat == -1) continue;
                ConnectedPlayer seated = found.getPlayersAtTable()[seat];
                cancelPendingDeparture(found, seat);
                logger.info("Player {} already seated at lobby {} seat {} — reconnecting",
                        seated.playerRecord.getName(), found.getId(), seat);
                return new LobbyAssignment(found, seat, getSessionBuyIn(userId));
            }
        }
    }

    /**
     * A player who disconnected while ALL_IN is queued to be removed when the
     * hand ends. If they come back before then, they keep their seat.
     */
    public void cancelPendingDeparture(Lobby lobby, int seat) {
        synchronized (lobby) {
            if (lobby.getPendingDepartureSeats().remove(seat)) {
                logger.info("Seat {} in lobby {} reconnected — no longer pending departure",
                        seat, lobby.getId());
            }
        }
    }

    /** Total chips the player has bought in for (buy-in + rebuys) at their current seat; 0 if unseated. */
    public int getSessionBuyIn(int userId) {
        return sessionBuyInsByPlayerId.getOrDefault(userId, 0);
    }

    /** Simple value object returned by joinLobby. */
    public record LobbyAssignment(Lobby lobby, int seat, int buyIn) {}

    private Lobby createLobby(StakesLevel stakes, boolean schoolLobby, String collegeDomain) {
        if (schoolLobby) {
            return new Lobby(LobbyType.UNIVERSITY, stakes, collegeDomain);
        }
        return new Lobby(LobbyType.GLOBAL, stakes);
    }

    /** Returns the first open seat index, or -1 if the lobby is full. */
    private int findOpenSeat(Lobby lobby) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            if (table[i] == null) return i;
        }
        return -1;
    }

    private String buildKey(StakesLevel stakes, String collegeDomain) {
        return collegeDomain == null
                ? "GLOBAL:" + stakes.name()
                : "SCHOOL:" + collegeDomain + ":" + stakes.name();
    }

    public Lobby getLobbyById(String lobbyId) {
        return lobbyById.get(lobbyId);
    }

    /** Finds the seat index for a player by userId, or -1 if not found. */
    public int getSeatForPlayer(Lobby lobby, int userId) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            if (table[i] != null && table[i].playerRecord.getId().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tops up a seated player's active chips from their bank balance, capped
     * so the stack never exceeds the table maximum buy-in. Chips can't be
     * added to a stack that's live in the current hand (table stakes); a
     * player sitting the hand out (busted, folded, or joined mid-hand) may
     * rebuy, and plays the new chips from the next hand.
     *
     * @return the amount actually added
     * @throws SeatingException if the rebuy breaks one of those rules or the
     *         bank can't cover it
     */
    public int rebuy(Lobby lobby, int userId, int amount) {
        synchronized (lobby) {
            int seat = getSeatForPlayer(lobby, userId);
            if (seat == -1) throw new SeatingException("You are not seated in this lobby.");
            ConnectedPlayer player = lobby.getPlayersAtTable()[seat];

            boolean handInProgress = lobby.getGamePhase() != GamePhase.WAITING
                    && lobby.getGamePhase() != GamePhase.HAND_COMPLETE;
            // Any non-folded player with cards or chips committed to this hand
            // is live in it (an all-in on the blind has chips in but may hold
            // no cards). The only non-folded player with neither is one who
            // busted last hand and was never dealt into this one.
            boolean inCurrentHand = handInProgress
                    && player.getPlayerState() != PlayerState.FOLDED
                    && (!player.getCards().isEmpty()
                            || lobby.getHandContributions().getOrDefault(player, 0) > 0);
            if (inCurrentHand) {
                throw new SeatingException("You can't add chips during a hand you're playing. "
                        + "Try again once it's over.");
            }

            int room = maxBuyIn(lobby.getBigBlind()) - player.getActiveChips();
            if (room <= 0) {
                throw new SeatingException("Your stack is already at the table maximum of "
                        + maxBuyIn(lobby.getBigBlind()) + ".");
            }
            int toAdd = Math.min(amount, room);
            if (toAdd <= 0) return 0;

            if (playerRepository.withdrawFromBank(userId, toAdd) == 0) {
                throw new SeatingException("Insufficient bank balance.");
            }
            player.addChips(toAdd);
            sessionBuyInsByPlayerId.merge(userId, toAdd, Integer::sum);

            // addChips flips a busted (0-chip, ALL_IN from their last hand)
            // player back to ACTIVE. Mid-hand that would hand them turns in a
            // hand they were never dealt into — keep them sitting out until
            // resetLobby re-activates every funded player at the next deal.
            if (handInProgress) {
                player.setPlayerState(PlayerState.FOLDED);
            }

            logger.info("Player {} rebought {} chips at lobby {} seat {}",
                    player.playerRecord.getName(), toAdd, lobby.getId(), seat);

            // Always broadcast so both clients see the updated chip count.
            lobbyGameService.broadcastState(lobby);

            // If the table is idle (not enough funded players to start), kick off
            // a hand start on the scheduler thread — same mechanism as scheduleNextHand.
            // Using the scheduler avoids calling game-state mutations from within this
            // synchronized block and keeps error handling in one place.
            if (lobby.getGamePhase() == GamePhase.WAITING) {
                lobbyGameService.scheduleHandAfterRebuy(lobby);
            }

            return toAdd;
        }
    }

    /** Removes a player from their seat (on disconnect or leave). */
    public void leaveTable(Lobby lobby, int seat) {
        synchronized (lobby) {
            ConnectedPlayer player = lobby.getPlayersAtTable()[seat];
            if (player == null) return;

            // Cash out: whatever is left on the table goes back to the bank.
            // If the DB write fails, keep them seated rather than vacate the
            // seat and lose their chips — a later leave/kick retries — and
            // don't throw into the caller (hand-conclusion paths call this).
            if (!cashOut(player, player.getActiveChips())) return;
            lobby.getPlayersAtTable()[seat] = null;

            logger.info("Player {} left lobby {} seat {}", player.playerRecord.getName(),
                    lobby.getId(), seat);

            // Registry membership is guarded separately from the lobby's own
            // game state — see registryLock's javadoc. Decided while still
            // holding the lobby lock so a concurrent joinLobby (which checks
            // registration under this same lock) can't seat someone into a
            // lobby that's being retired.
            if (lobby.tableCount() == 0) {
                synchronized (registryLock) {
                    lobbyById.remove(lobby.getId());
                    lobbies.values().forEach(pool -> pool.remove(lobby));
                }
                logger.info("Lobby {} is empty — removed", lobby.getId());
            }
        }
    }

    /**
     * Credits {@code amount} chips to the player's bank and settles this
     * seat's profit. Caller holds the seat's lobby lock. Returns false (and
     * changes nothing) if the database write failed.
     */
    private boolean cashOut(ConnectedPlayer player, int amount) {
        Integer userId = player.playerRecord.getId();
        int boughtIn = sessionBuyInsByPlayerId.getOrDefault(userId, 0);
        try {
            playerRepository.depositToBank(userId, amount, amount - boughtIn);
        } catch (RuntimeException e) {
            logger.error("Cash-out of {} chips for player {} failed — keeping them seated",
                    amount, userId, e);
            return false;
        }
        sessionBuyInsByPlayerId.remove(userId);
        return true;
    }

    /**
     * Graceful shutdown (e.g. a redeploy): every table lives only in memory,
     * so without this every seated player's buy-in would simply vanish from
     * their bank. Returns each player's stack plus, for a hand still being
     * played, whatever they've committed to it so far (that hand will never
     * be finished, so its pots are unwound).
     */
    @PreDestroy
    public void cashOutAllOnShutdown() {
        List<Lobby> all;
        synchronized (registryLock) {
            all = new ArrayList<>(lobbyById.values());
        }
        for (Lobby lobby : all) {
            synchronized (lobby) {
                boolean handInProgress = lobby.getGamePhase() != GamePhase.WAITING
                        && lobby.getGamePhase() != GamePhase.HAND_COMPLETE;
                ConnectedPlayer[] table = lobby.getPlayersAtTable();
                Set<ConnectedPlayer> seated = new HashSet<>();
                for (int seat = 0; seat < Lobby.TABLE_SIZE; seat++) {
                    ConnectedPlayer player = table[seat];
                    if (player == null) continue;
                    seated.add(player);
                    int refund = player.getActiveChips()
                            + (handInProgress ? lobby.getHandContributions().getOrDefault(player, 0) : 0);
                    if (cashOut(player, refund)) table[seat] = null;
                }
                if (handInProgress) refundDepartedContributions(lobby, seated);
            }
        }
    }

    /**
     * A player who folded and left mid-hand already cashed out their stack,
     * with what they'd put in the pot settled as a loss. The pot was going to
     * the hand's winner; unwound instead, those chips go back to the player
     * who bet them (and the loss is reversed) rather than vanishing.
     * {@code seated} were refunded by the seat loop — ConnectedPlayer equality
     * is by player ID, so this also skips someone who left and sat back down
     * at this same table.
     */
    private void refundDepartedContributions(Lobby lobby, Set<ConnectedPlayer> seated) {
        for (Map.Entry<ConnectedPlayer, Integer> e : lobby.getHandContributions().entrySet()) {
            int contributed = e.getValue();
            if (contributed <= 0 || seated.contains(e.getKey())) continue;
            Integer userId = e.getKey().playerRecord.getId();
            try {
                playerRepository.depositToBank(userId, contributed, contributed);
            } catch (RuntimeException ex) {
                logger.error("Shutdown refund of {} pot chips to departed player {} failed",
                        contributed, userId, ex);
            }
        }
    }
}
