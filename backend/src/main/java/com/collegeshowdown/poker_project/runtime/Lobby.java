package com.collegeshowdown.poker_project.runtime;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.collegeshowdown.poker_project.model.Player;
import com.collegeshowdown.poker_project.model.Winner;

import java.util.ArrayList;
import java.util.Deque;

// NON PERSISTENT !!!!!!
public class Lobby {
    public static final int TABLE_SIZE = 8;

    enum LobbyType {
        SCHOOL,
        GLOBAL,
        CUSTOM
    }

    private final static Logger logger = LoggerFactory.getLogger(Player.class);

    private String id = UUID.randomUUID().toString();

    private LobbyType lobbyType;
    private String associatedSchool; // make this a custom type later; we discuss.
    private Object customLobbyOptions; // define later what these may be.
    private String customLobbyCode;
    private String lobbyInfo;
    private Deque<ConnectedPlayer> queuedPlayers;
    private ConnectedPlayer activePlayers[] = new ConnectedPlayer[TABLE_SIZE]; // the table

    public Lobby() {}

    public ConnectedPlayer[] getBoard() {
        return activePlayers;
    }

    public ArrayList<ConnectedPlayer> getQueue() {
        return new ArrayList<>(queuedPlayers);
    }

    public Lobby(LobbyType lobbyType, String associatedSchool, Object customLobbyOptions, String customLobbyCode,
            String lobbyInfo) {
        this.lobbyType = lobbyType;
        this.associatedSchool = associatedSchool;
        this.customLobbyOptions = customLobbyOptions;
        this.customLobbyCode = customLobbyCode;
        this.lobbyInfo = lobbyInfo;
    }

    public void addPlayerToQueue(ConnectedPlayer player) {
        queuedPlayers.push(player);
    }

    public boolean fillLobby(int num_to_insert) {
        // fill table with <num> players. fails if no players could be inserted OR if
        // table is full (check logs for more).
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
            ConnectedPlayer p;
            try {
                p = queuedPlayers.pop();
            } catch (NoSuchElementException e) {
                logger.warn("No queued players to pop for lobby" + id + "!");
                return insertionCount > 0 ? true : false;
            }

            // we know there is at least one empty slot; find as many as possible and put
            // all the players in.

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
        for (ConnectedPlayer p : activePlayers) {
            if (p != null)
                count++;
        }

        return count;
    }

    public boolean play() { return true; } // TODO: implement lol

    public ArrayList<Winner> getWinners() { return new ArrayList<>(); }
}