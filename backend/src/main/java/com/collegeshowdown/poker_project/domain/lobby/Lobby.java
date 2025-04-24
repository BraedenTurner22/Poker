package com.collegeshowdown.poker_project.domain.lobby;

import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.models.*;
import com.collegeshowdown.poker_project.domain.card.*;

import java.util.UUID;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import com.collegeshowdown.poker_project.services.*;

@Component @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Lobby {

    private final static Logger logger = LoggerFactory.getLogger(Lobby.class);

    public static final int TABLE_SIZE = 8;

    @Column(nullable = false)
    private final LobbyType lobbyType;

    @Id @Column(nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column
    private School associatedSchool;

    @Transient
    private ConnectedPlayer playersAtTable[] = new ConnectedPlayer[TABLE_SIZE];

    @Transient
    private List<ConnectedPlayer> winners = new ArrayList<>();

    @Column
    private int currentPlayerIndex;

    @Transient
    private List<Pot> allActivePots = new ArrayList<>();

    @Column(nullable = false)
    private boolean isLowStakes;

    @Column(nullable = false)
    private final int smallBlind;

    @Column(nullable = false)
    private final int bigBlind;

    @Column
    private int smallBlindIndex;

    @Column
    private int bigBlindIndex;

    @Transient
    private List<Card> board = new ArrayList<>();

    // Required for JPA
    protected Lobby() {
        this.lobbyType = null;
        this.smallBlind = 0;
        this.bigBlind = 0;
    }



    // Global Lobby
    public Lobby(boolean isLowStakes) {
        this.lobbyType = LobbyType.GLOBAL;
        this.isLowStakes = isLowStakes;
        this.smallBlind = isLowStakes ? 10 : 20;
        this.bigBlind = isLowStakes ? 20 : 50;
        this.smallBlindIndex = 0;
        this.bigBlindIndex = 1;
    }


    // School lobby
    public Lobby(School associatedSchool, boolean isLowStakes) {
        this.lobbyType = LobbyType.UNIVERSITY;
        this.associatedSchool = associatedSchool;
        this.isLowStakes = isLowStakes;
        this.smallBlind = isLowStakes ? 10 : 20;
        this.bigBlind = isLowStakes ? 20 : 50;
        this.smallBlindIndex = 0;
        this.bigBlindIndex = 1;
    }

    public Lobby(CustomLobbyInfo lobbyInfo, boolean isLowStakes) {
        this.lobbyType = LobbyType.CUSTOM;
        this.isLowStakes = isLowStakes;
        this.smallBlind = isLowStakes ? 10 : 20;
        this.bigBlind = isLowStakes ? 20 : 50;
        this.smallBlindIndex = 0;
        this.bigBlindIndex = 1;
    }

    public int tableCount() {
        // number of players in the table
        int count = 0;
        for (ConnectedPlayer connectedPlayer : playersAtTable) {
            if (connectedPlayer != null)
                count++;
        }
        return count;
    }



    // Helper function determining which ConnectedPlayers are not null, haven't
    // folded, and have an ActiveChip stack > 0
    public List<ConnectedPlayer> getActivePlayersList() {
        return Arrays.stream(playersAtTable).filter(Objects::nonNull).filter(ConnectedPlayer::isActive)
                .collect(Collectors.toList());
    }



    // Regular Getters and setters
    public String getId() {
        return id;
    }



    public void setId(String id) {
        this.id = id;
    }



    public LobbyType getLobbyType() {
        return lobbyType;
    }



    public School getAssociatedSchool() {
        return associatedSchool;
    }



    public void setAssociatedSchool(School associatedSchool) {
        this.associatedSchool = associatedSchool;
    }



    public List<Card> getBoard() {
        return this.board;
    }



    public void setBoard(List<Card> board) {
        this.board = board;
    }



    public ConnectedPlayer[] getPlayersAtTable() {
        return playersAtTable;
    }



    public void setPlayers(ConnectedPlayer[] activePlayers) {
        this.playersAtTable = activePlayers;
    }



    public List<ConnectedPlayer> getWinners() {
        return winners;
    }



    public void setWinners(List<ConnectedPlayer> winners) {
        this.winners = winners;
    }



    public int getSmallBlind() {
        return smallBlind;
    }



    public int getBigBlind() {
        return bigBlind;
    }



    public List<Pot> getAllActivePots() {
        return allActivePots;
    }



    public void setAllActivePots(List<Pot> allActivePots) {
        this.allActivePots = allActivePots;
    }



    public Pot getCurrentPot() {
        if (allActivePots.isEmpty()) {
            return null;
        }
        return allActivePots.get(allActivePots.size() - 1);
    }



    public ConnectedPlayer getCurrentPlayer() {
        if (tableCount() == 0) {
            return null;
        }
        return playersAtTable[currentPlayerIndex % playersAtTable.length];
    }



    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }



    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }



    public int getSmallBlindIndex() {
        return smallBlindIndex;
    }



    public void setSmallBlindIndex(int smallBlindIndex) {
        this.smallBlindIndex = smallBlindIndex;
    }



    public int getBigBlindIndex() {
        return bigBlindIndex;
    }



    public void setBigBlindIndex(int bigBlindIndex) {
        this.bigBlindIndex = bigBlindIndex;
    }



    public boolean isLowStakes() {
        return isLowStakes;
    }



    public void setLowStakes(boolean isLowStakes) {
        this.isLowStakes = isLowStakes;
    }



    @Override
    public String toString() {
        return "Lobby{" + "id=" + id + '\'' + ", smallBlind=" + smallBlind + ", bigBlind=" + bigBlind + ", activePots="
                + allActivePots.size() + ", board=" + board + ", players="
                + Arrays.stream(playersAtTable).filter(Objects::nonNull).map(player -> player.playerRecord.getId())
                        .collect(Collectors.toList())
                + ", winners=" + winners + '}';
    }
}