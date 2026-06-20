package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.analysis.AnalysisEngine;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WinnerService {
    private final static Logger logger = LoggerFactory.getLogger(WinnerService.class);

    /**
     * Determines winners and distributes all active pots.
     *
     * Steps:
     *  1. Add community cards to each non-folded player's card list.
     *  2. Run hand analysis on each non-folded player.
     *  3. For each pot (side pots first, main pot last), evaluate winners among
     *     ONLY that pot's eligible players — so an all-in player with the best
     *     hand can't "win" a side pot they can't compete in.
     *  4. Distribute chips and update both the persistent record and live stack.
     */
    public void determineWinners(Lobby lobby) {
        List<ConnectedPlayer> nonFolded = getNonFoldedPlayers(lobby);

        // Give each player the full 7-card hand (2 hole + 5 board) before analysis
        nonFolded.forEach(p -> {
            p.addCards(lobby.getBoard());
            AnalysisEngine.analyzeHand(p);
        });

        List<Pot> allPots = lobby.getAllActivePots();
        List<ConnectedPlayer> handWinners = new ArrayList<>();

        // Process side pots first (reverse order), then main pot
        for (int i = allPots.size() - 1; i >= 0; i--) {
            Pot pot = allPots.get(i);
            List<ConnectedPlayer> eligible = pot.getEligiblePlayers();

            if (eligible.isEmpty()) {
                logger.warn("Pot {} has no eligible players — skipping", i);
                continue;
            }

            ConnectedPlayer[] eligibleArray = eligible.toArray(new ConnectedPlayer[0]);
            List<ConnectedPlayer> potWinners = AnalysisEngine.getWinners(eligibleArray);

            if (!potWinners.isEmpty() && pot.getAmountInPot() > 0) {
                distributePot(pot, potWinners);
                handWinners.addAll(potWinners);
            } else if (potWinners.isEmpty()) {
                logger.warn("No winners found for pot {} — this should not happen", i);
            }
        }

        List<ConnectedPlayer> uniqueWinners = handWinners.stream().distinct().collect(Collectors.toList());
        lobby.setWinners(uniqueWinners);

        logger.info("Hand complete. Winners: {}",
                uniqueWinners.stream()
                        .map(p -> String.valueOf(p.playerRecord.getId()))
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Splits potSize evenly; remainder chips go one-by-one to first winners in list.
     * Updates BOTH the persistent chip record and the live in-game stack.
     */
    public void distributePot(Pot pot, List<ConnectedPlayer> winners) {
        if (winners.isEmpty()) return;

        int potSize = pot.getAmountInPot();
        int count = winners.size();
        int base = potSize / count;
        int remainder = potSize % count;

        logger.info("Distributing {} chips to {} winner(s)", potSize, count);

        for (int i = 0; i < count; i++) {
            ConnectedPlayer winner = winners.get(i);
            int amount = base + (i < remainder ? 1 : 0);

            // Winnings go to the live table stack only; the persistent bank is
            // settled when the player cashes out via leaveTable.
            winner.addChips(amount);

            logger.info("Player {} receives {} chips", winner.playerRecord.getId(), amount);
        }

        pot.emptyPot();
    }

    private List<ConnectedPlayer> getNonFoldedPlayers(Lobby lobby) {
        List<ConnectedPlayer> result = new ArrayList<>();
        for (ConnectedPlayer p : lobby.getPlayersAtTable()) {
            if (p != null && p.getPlayerState() != PlayerState.FOLDED) {
                result.add(p);
            }
        }
        return result;
    }
}
