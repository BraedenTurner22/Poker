package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PotManagerService {
    private final static Logger logger = LoggerFactory.getLogger(PotManagerService.class);

    /**
     * Posts small and big blinds. Seat indices are computed by the caller
     * (LobbyGameService) to avoid a circular bean dependency.
     *
     * Both blind amounts are capped at the player's stack, so short-stacked
     * players go all-in automatically.
     */
    public void postBlinds(Lobby lobby, int sbSeat, int bbSeat) {
        ConnectedPlayer[] table = lobby.getPlayersAtTable();
        ConnectedPlayer sbPlayer = table[sbSeat];
        ConnectedPlayer bbPlayer = table[bbSeat];

        // Single working pot — all active players are initially eligible
        Pot workingPot = new Pot(lobby.getActivePlayersList());
        List<Pot> pots = new ArrayList<>();
        pots.add(workingPot);
        lobby.setAllActivePots(pots);

        // Small blind (capped at stack)
        int sbAmount = sbPlayer.payBlind(lobby.getSmallBlind(), workingPot);
        lobby.addHandContribution(sbPlayer, sbAmount);
        lobby.addRoundContribution(sbPlayer, sbAmount);

        // Big blind (capped at stack)
        int bbAmount = bbPlayer.payBlind(lobby.getBigBlind(), workingPot);
        lobby.addHandContribution(bbPlayer, bbAmount);
        lobby.addRoundContribution(bbPlayer, bbAmount);

        logger.info("Blinds posted — SB seat {} paid {}, BB seat {} paid {}",
                sbSeat, sbAmount, bbSeat, bbAmount);

        // If either blind went all-in, side pots may already be needed
        if (sbPlayer.getPlayerState() == PlayerState.ALL_IN
                || bbPlayer.getPlayerState() == PlayerState.ALL_IN) {
            recalculatePots(lobby);
        }
    }

    /**
     * The core side-pot algorithm. Call this at the end of every betting round.
     *
     * Algorithm:
     *   1. Sort all unique contribution amounts ascending.
     *   2. For each "slice" between consecutive levels, the pot gets
     *      (slice height) × (number of players who contributed at least that level).
     *   3. Players eligible to WIN a slice = non-folded contributors at that level.
     *
     * Example — A: 100 (all-in), B: 500, C: 300, D: 200 (folded)
     *   Main pot  (0–100):  100 × 4 = 400  →  eligible: A, B, C
     *   Side pot1 (100–200): 100 × 3 = 300  →  eligible: B, C  (D folded)
     *   Side pot2 (200–300): 100 × 2 = 200  →  eligible: B, C
     *   Side pot3 (300–500): 200 × 1 = 200  →  eligible: B
     *   Total: 1100 ✓
     */
    public void recalculatePots(Lobby lobby) {
        Map<ConnectedPlayer, Integer> contributions = lobby.getHandContributions();
        if (contributions.isEmpty()) return;

        // Sorted unique contribution levels
        List<Integer> levels = contributions.values().stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<Pot> newPots = new ArrayList<>();
        int prevLevel = 0;

        for (int level : levels) {
            if (level <= prevLevel) continue;

            int slicePerPlayer = level - prevLevel;

            // Everyone who put in at least this much (includes folded — their chips count)
            List<ConnectedPlayer> contributors = contributions.entrySet().stream()
                    .filter(e -> e.getValue() >= level)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            // Non-folded subset can actually WIN this slice
            List<ConnectedPlayer> eligible = contributors.stream()
                    .filter(p -> p.getPlayerState() != PlayerState.FOLDED)
                    .collect(Collectors.toList());

            // If only folded players contributed at this level (e.g. the big
            // blind folds pre-flop), their excess must not vanish — it goes
            // to whoever is still in the hand.
            if (eligible.isEmpty()) {
                eligible = contributions.keySet().stream()
                        .filter(p -> p.getPlayerState() != PlayerState.FOLDED)
                        .collect(Collectors.toList());
            }

            int potAmount = slicePerPlayer * contributors.size();

            if (potAmount > 0 && !eligible.isEmpty()) {
                newPots.add(new Pot(potAmount, eligible));
            }

            prevLevel = level;
        }

        lobby.setAllActivePots(newPots);
        logger.info("Pots recalculated for lobby {}: {} pot(s) totalling {} chips",
                lobby.getId(),
                newPots.size(),
                newPots.stream().mapToInt(Pot::getAmountInPot).sum());
    }
}
