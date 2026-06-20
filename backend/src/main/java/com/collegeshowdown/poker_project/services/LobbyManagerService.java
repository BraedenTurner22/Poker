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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LobbyManagerService {

    private final static Logger logger = LoggerFactory.getLogger(LobbyManagerService.class);

    private final LobbyGameService lobbyGameService;
    private final PlayerRepository playerRepository;

    /** key: "GLOBAL:LOW", "GLOBAL:MEDIUM", "GLOBAL:HIGH", "SCHOOL:<domain>:LOW", etc. */
    private final Map<String, List<Lobby>> lobbies = new ConcurrentHashMap<>();

    /** Fast lookup: lobbyId → Lobby */
    private final Map<String, Lobby> lobbyById = new ConcurrentHashMap<>();

    /**
     * Guards the {@code lobbies}/{@code lobbyById} maps — i.e. which lobbies
     * exist and pool membership. Deliberately separate from any individual
     * Lobby's own lock (synchronized(lobby)) and never held at the same time
     * as one, so the two can never deadlock against each other.
     */
    private final Object registryLock = new Object();

    @Autowired
    public LobbyManagerService(LobbyGameService lobbyGameService,
            PlayerRepository playerRepository) {
        this.lobbyGameService = lobbyGameService;
        this.playerRepository = playerRepository;
    }

    /**
     * Finds an existing lobby with an open seat that matches the given criteria,
     * or creates a new one. Places the player in a seat and starts the hand if
     * the table reaches 2+ players.
     *
     * Returns the assigned [lobby, seatIndex] pair.
     */
    public LobbyAssignment joinLobby(PlayerRecord record,
            StakesLevel stakes, boolean schoolLobby, int requestedBuyIn) {

        // Retries if a concurrent join wins the race for the lobby's last
        // open seat between the registry pick and the per-lobby seating step.
        while (true) {
            Lobby lobby;
            synchronized (registryLock) {
                // If this account is already seated anywhere, reconnect them to
                // that seat instead of seating them twice.
                for (Lobby existing : lobbyById.values()) {
                    int existingSeat = getSeatForPlayer(existing, record.getId());
                    if (existingSeat != -1) {
                        ConnectedPlayer seated = existing.getPlayersAtTable()[existingSeat];
                        logger.info("Player {} already seated at lobby {} seat {} — reconnecting",
                                record.getName(), existing.getId(), existingSeat);
                        return new LobbyAssignment(existing, existingSeat, seated.getActiveChips());
                    }
                }

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

            // Seat the player. Buy-in moves chips from the bank to the table;
            // they return via leaveTable's cash-out.
            synchronized (lobby) {
                int seat = findOpenSeat(lobby);
                if (seat == -1) {
                    // Lost the race for the last seat — pick again.
                    continue;
                }

                int maxBuyIn = 100 * stakes.bigBlind;
                int buyIn = requestedBuyIn > 0
                        ? Math.min(requestedBuyIn, Math.min(record.getTotalChips(), maxBuyIn))
                        : Math.min(record.getTotalChips(), maxBuyIn);
                record.updateChips(-buyIn);
                playerRepository.save(record);

                ConnectedPlayer player = new ConnectedPlayer(record);
                player.buyIn(buyIn);

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
     * Tops up a player's active chips from their bank balance.
     * Only valid while the player is still seated (typically called at HAND_COMPLETE
     * when they have 0 chips). Returns the actual amount added.
     */
    public int rebuy(Lobby lobby, int seat, PlayerRecord record, int amount) {
        synchronized (lobby) {
            ConnectedPlayer player = lobby.getPlayersAtTable()[seat];
            if (player == null) throw new IllegalStateException("No player at seat " + seat);

            int capped = Math.min(amount, record.getTotalChips());
            if (capped <= 0) return 0;

            record.updateChips(-capped);
            playerRepository.save(record);
            player.addChips(capped);
            logger.info("Player {} rebought {} chips at lobby {} seat {}",
                    record.getName(), capped, lobby.getId(), seat);

            // Always broadcast so both clients see the updated chip count.
            lobbyGameService.broadcastState(lobby);

            // If the table is idle (not enough funded players to start), kick off
            // a hand start on the scheduler thread — same mechanism as scheduleNextHand.
            // Using the scheduler avoids calling game-state mutations from within this
            // synchronized block and keeps error handling in one place.
            if (lobby.getGamePhase() == GamePhase.WAITING) {
                lobbyGameService.scheduleHandAfterRebuy(lobby);
            }

            return capped;
        }
    }

    /** Removes a player from their seat (on disconnect or leave). */
    public void leaveTable(Lobby lobby, int seat) {
        boolean nowEmpty;
        synchronized (lobby) {
            ConnectedPlayer player = lobby.getPlayersAtTable()[seat];
            if (player == null) return;

            // Cash out: whatever is left on the table goes back to the bank
            player.playerRecord.updateChips(player.getActiveChips());
            playerRepository.save(player.playerRecord);
            lobby.getPlayersAtTable()[seat] = null;

            logger.info("Player {} left lobby {} seat {}", player.playerRecord.getName(),
                    lobby.getId(), seat);

            nowEmpty = lobby.tableCount() == 0;
        }

        // Registry membership is guarded separately from the lobby's own game
        // state — see registryLock's javadoc. Re-checks tableCount() since
        // it's a fresh, unrelated lock acquisition; best-effort cleanup, not
        // safety-critical (worst case an empty lobby briefly lingers).
        if (nowEmpty) {
            synchronized (registryLock) {
                if (lobby.tableCount() == 0) {
                    lobbyById.remove(lobby.getId());
                    lobbies.values().forEach(pool -> pool.remove(lobby));
                    logger.info("Lobby {} is empty — removed", lobby.getId());
                }
            }
        }
    }
}
