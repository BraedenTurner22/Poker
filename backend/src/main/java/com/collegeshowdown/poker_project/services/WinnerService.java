package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.analysis.AnalysisEngine;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.lobby.*;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WinnerService {
    private final static Logger logger = LoggerFactory.getLogger(WinnerService.class);

    /**
     * Determine the winners of the current game.
     */
    @Transactional
    public void determineWinners(Lobby lobby) {
        logger.info("Determining winners");

        ConnectedPlayer[] playersAtTable = lobby.getPlayersAtTable();
        List<ConnectedPlayer> winners = AnalysisEngine.getWinners(playersAtTable);
        lobby.setWinners(winners);

        // Process all pots - should be done in reverse order (side pots first)
        List<Pot> allPots = lobby.getAllActivePots();
        for (int i = allPots.size() - 1; i >= 0; i--) {
            Pot pot = allPots.get(i);

            // Find winners eligible for this pot
            List<ConnectedPlayer> potWinners = winners.stream()
                    .filter(player -> pot.getEligiblePlayers().contains(player)).toList();

            if (!potWinners.isEmpty()) {
                distributePot(pot, potWinners);
            } else {
                logger.warn("No eligible winners found for pot {}", i);
            }
        }

        logger.info("Winners: {}", winners);
    }



    /**
     * Distribute a specific pot to winners
     */
    @Transactional
    public void distributePot(Pot pot, List<ConnectedPlayer> winners) {
        if (winners.isEmpty()) {
            logger.warn("No winners to distribute pot to");
            return;
        }

        int potSize = pot.getSize();
        int winnerCount = winners.size();
        int baseAmount = potSize / winnerCount;
        int remainder = potSize % winnerCount;

        logger.info("Distributing pot of {} chips to {} winners", potSize, winnerCount);

        for (int i = 0; i < winnerCount; i++) {
            ConnectedPlayer winner = winners.get(i);
            int amount = baseAmount;

            // Distribute remainder (if any) to first players in list
            if (i < remainder) {
                amount++;
            }

            winner.addChips(amount);
            logger.info("Player {} received {} chips", winner.playerRecord.getId(), amount);
        }
    }



    /**
     * Get winners for a specific pot
     */
    public List<ConnectedPlayer> getWinnersForPot(Pot pot, List<ConnectedPlayer> allWinners) {
        return allWinners.stream().filter(player -> pot.getEligiblePlayers().contains(player)).toList();
    }
}