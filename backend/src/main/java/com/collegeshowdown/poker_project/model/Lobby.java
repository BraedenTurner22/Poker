package com.collegeshowdown.poker_project.model;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Deque;

@Entity
public class Lobby implements Serializable {
    public static final int TABLE_SIZE = 8;

    enum LobbyType {
        SCHOOL,
        GLOBAL,
        CUSTOM
    }

    private final static Logger logger = LoggerFactory.getLogger(Player.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false, unique = true)
    private int id;

    private LobbyType lobbyType;
    private String associatedSchool; // make this a custom type later; we discuss.
    private Object customLobbyOptions; // define later what these may be.

    private String customLobbyCode;
    private String lobbyInfo;
    private Deque<Player> queuedPlayers;
    private Player activePlayers[] = new Player[TABLE_SIZE]; // the table

    public Lobby() {}

    public Lobby(LobbyType lobbyType, String associatedSchool, Object customLobbyOptions, String customLobbyCode, String lobbyInfo) {
        this.lobbyType = lobbyType;
        this.associatedSchool = associatedSchool;
        this.customLobbyOptions = customLobbyOptions;
        this.customLobbyCode = customLobbyCode;
        this.lobbyInfo = lobbyInfo;
    }

    public void addPlayerToQueue(Player player) {
        queuedPlayers.push(player);
    }

    public boolean fillLobby(int num_to_insert) {
        // fill table with <num> players. fails if no players could be inserted OR if table is full (check logs for more).
        // TODO: insert custom return value with more info??

        int tableCount = tableCount();

        if (tableCount == TABLE_SIZE) {
            // we alr have <num> capacity - cannot add any more.
            logger.warn("Lobby " + id + " is already at max capacity - cannot add " + num_to_insert + " players.");
            return false;
        }

        // insert them naively, from left to right. pop as many as possible.

        int insertionCount = 0;

        while (tableCount < TABLE_SIZE && insertionCount < num_to_insert) {
            Player p;
            try {
                p = queuedPlayers.pop();
            } catch(NoSuchElementException e) {
                logger.warn("No queued players to pop for lobby" + id + "!");
                return insertionCount > 0 ? true : false;
            }

            // we know there is at least one empty slot; find as many as possible and put all the players in.

            for (int i = 0; i < TABLE_SIZE; i++) {
                if (activePlayers[i] == null) {
                    activePlayers[i] = p;
                    insertionCount++;
                    tableCount++;
                }
            }
        }

        logger.info("Inserted " + insertionCount + " players into lobby " + id);
        return true;
    }

    private int tableCount() {
        // number of players in the table
        int count = 0;
        for (Player p : activePlayers) {
            if (p != null) count++;
        }

        return count;
    }
}