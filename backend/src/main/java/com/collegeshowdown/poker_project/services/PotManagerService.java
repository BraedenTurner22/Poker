package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.lobby.*;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PotManagerService {
    private final static Logger logger = LoggerFactory.getLogger(PotManagerService.class);

    private final LobbyGameService lobbyGameService;

    @Autowired
    public PotManagerService(LobbyGameService lobbyGameService) {
        this.lobbyGameService = lobbyGameService;
    }



    /**
     * Creates and manages pots in the game
     */
    public Pot createMainPot(Lobby lobby) {
        List<ConnectedPlayer> activePlayers = lobby.getActivePlayersList();
        int minBet = lobby.getBigBlind();
        int leastChipCount = minimumChipCountAmong(activePlayers);

        Pot pot = new Pot(minBet, leastChipCount, activePlayers);
        List<Pot> allPots = new ArrayList<>();
        allPots.add(pot);
        lobby.setAllActivePots(allPots);

        return pot;
    }



    /**
     * Gets blinds out of two players. Blinds start at indexes 0 and 1 when the
     * lobby starts. Each time a hand is played, the blind indices shift clockwise
     * to the next available ConnectedPlayer. If a player does not have enough chips
     * to cover the small or big blind, they are all in, and (later) a side pot
     * should be created.
     *
     * IN ORDER TO PLAY, PLAYERS MUST HAVE AT LEAST THE SMALL BLIND
     */
    public void extractBlinds(Lobby lobby) {
        ConnectedPlayer[] playersAtTable = lobby.getPlayersAtTable();
        int smallBlind = lobby.getSmallBlind();
        int bigBlind = lobby.getBigBlind();

        // build fresh list of in-hand players
        List<ConnectedPlayer> allActivePlayersAfterReset = lobby.getActivePlayersList();

        // kick off the first pot at the min-stack
        int leastChipCountOfInitialPlayer = minimumChipCountAmong(allActivePlayersAfterReset);
        Pot firstPot = new Pot(bigBlind, leastChipCountOfInitialPlayer, allActivePlayersAfterReset);
        List<Pot> allPots = new ArrayList<>();
        allPots.add(firstPot);
        lobby.setAllActivePots(allPots);
        Pot currentPot = firstPot;

        // 2) find blind seats
        int sbSeat = lobbyGameService.getFirstValidIndex(lobby, lobby.getSmallBlindIndex());
        int bbSeat = lobbyGameService.getFirstValidIndex(lobby, lobby.getBigBlindIndex());
        if (bbSeat == sbSeat) {
            bbSeat = lobbyGameService.getNextValidIndex(lobby, sbSeat);
        }
        ConnectedPlayer sbPlayer = playersAtTable[sbSeat];
        ConnectedPlayer bbPlayer = playersAtTable[bbSeat];

        // Contribute small blind (all-in if < blind)
        sbPlayer.payBlind(smallBlind, currentPot);

        int bbContrib = Math.min(bbPlayer.getActiveChips(), bigBlind);
        if (bbContrib < bigBlind) {
            bbPlayer.payBlind(smallBlind, currentPot);
            int chipsOfPlayerWithMinStack = minimumChipCountAmong(lobby.getActivePlayersList());
            Pot splitBlindPot = new Pot(bigBlind - smallBlind, chipsOfPlayerWithMinStack, lobby.getActivePlayersList());
            lobby.getAllActivePots().add(splitBlindPot);
        } else {
            bbPlayer.payBlind(bigBlind, currentPot);
        }

        logger.info("Blinds posted: SB={} by {}, BB={} by {} → Pots: {}", smallBlind, sbPlayer.playerRecord.getId(),
                bbContrib, bbPlayer.playerRecord.getId(), lobby.getAllActivePots().size());
    }



    // Helper function finding smallest active-chips among a list
    private int minimumChipCountAmong(List<ConnectedPlayer> players) {
        return players.stream().mapToInt(ConnectedPlayer::getActiveChips).min().orElse(0);
    }



    /**
     * Creates a side pot when a player goes all-in
     */
    public Pot createSidePot(Lobby lobby, int betSize, List<ConnectedPlayer> eligiblePlayers) {
        int leastChipCount = minimumChipCountAmong(eligiblePlayers);
        Pot sidePot = new Pot(betSize, leastChipCount, eligiblePlayers);
        lobby.getAllActivePots().add(sidePot);

        logger.info("Created side pot with bet size {} and {} eligible players", betSize, eligiblePlayers.size());
        return sidePot;
    }
}