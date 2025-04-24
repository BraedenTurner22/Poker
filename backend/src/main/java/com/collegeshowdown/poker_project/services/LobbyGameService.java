package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LobbyGameService {
    private final static Logger logger = LoggerFactory.getLogger(LobbyGameService.class);

    private final DealerService dealerService;
    private final PotManagerService potManagerService;
    private final WinnerService winnerService;

    @Autowired
    public LobbyGameService(DealerService dealerService, PotManagerService potManagerService,
            WinnerService winnerService) {
        this.dealerService = dealerService;
        this.potManagerService = potManagerService;
        this.winnerService = winnerService;
    }



    /**
     * Start playing the poker game.
     */
    @Transactional
    public void play(Lobby lobby) {
        logger.info("Starting a new game with {} players", lobby.tableCount());

        if (lobby.tableCount() < 2) {
            logger.warn("Cannot start game with fewer than 2 players");
            return;
        }

        while (lobby.tableCount() >= 2) {
            // Reset lobby state
            resetLobby(lobby);

            // Extract Blinds
            potManagerService.extractBlinds(lobby);

            // Initiate Betting
            // (betting logic would go here)

            // Deal 2 cards to each player
            dealerService.dealHoleCards(lobby);

            // Initiate Betting
            // (betting logic would go here)

            // Deal the flop (3 cards)
            dealerService.dealFlop(lobby);

            // Initiate Betting
            // (betting logic would go here)

            // Deal the turn (1 card)
            dealerService.dealTurn(lobby);

            // Initiate Betting
            // (betting logic would go here)

            // Deal the river (1 card)
            dealerService.dealRiver(lobby);

            // Initiate Betting
            // (betting logic would go here)

            // Determine the winners
            winnerService.determineWinners(lobby);

            // advance blinds for next hand
            int smallBlindIndex = lobby.getSmallBlindIndex() + 1;
            int bigBlindIndex = lobby.getBigBlindIndex() + 1;
            lobby.setSmallBlindIndex(smallBlindIndex);
            lobby.setBigBlindIndex(bigBlindIndex);
        }
    }



    /**
     * Reset the game state for a new round.
     */
    public void resetLobby(Lobby lobby) {
        logger.info("Resetting game state");

        // Reset dealer
        dealerService.resetDeck();

        // Reset lobby state
        lobby.getBoard().clear();
        lobby.getWinners().clear();
        lobby.getAllActivePots().clear();

        // clear each player's hand data
        for (ConnectedPlayer player : lobby.getActivePlayersList()) {
            player.getCards().clear();
            player.getBestCards().clear();
            player.setHandRank(null);
        }
    }



    /**
     * Returns the index of the first non-null player starting from the given index
     * (inclusive).
     */
    public int getFirstValidIndex(Lobby lobby, int startIndex) {
        ConnectedPlayer[] playersAtTable = lobby.getPlayersAtTable();
        int idx = startIndex % Lobby.TABLE_SIZE;
        int smallBlind = lobby.getSmallBlind();

        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            int candidate = (idx + i) % Lobby.TABLE_SIZE;
            if ((playersAtTable[candidate] != null) && (playersAtTable[candidate].getActiveChips() >= smallBlind)) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No valid player found in the lobby.");
    }



    /**
     * Returns the index of the next non-null player after the given index.
     */
    public int getNextValidIndex(Lobby lobby, int currentIndex) {
        ConnectedPlayer[] playersAtTable = lobby.getPlayersAtTable();
        int idx = (currentIndex + 1) % Lobby.TABLE_SIZE;
        int smallBlind = lobby.getSmallBlind();

        for (int i = 0; i < Lobby.TABLE_SIZE; i++) {
            int candidate = (idx + i) % Lobby.TABLE_SIZE;
            if ((playersAtTable[candidate] != null) && (playersAtTable[candidate].getActiveChips() > smallBlind)) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No next valid player found in the lobby.");
    }
}