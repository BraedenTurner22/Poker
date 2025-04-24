package com.collegeshowdown.poker_project.services;

import java.util.List;
import java.util.Deque;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collegeshowdown.poker_project.domain.lobby.*;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;

// TODO:
// add player queues for each school (every single school has it's own queue of players, who can then join their school's lobby. )

@Service
public class LobbyManagerService {

    // NEED LOGIC FOR ITERATING THROUGH ALL QUALIFIED LOBBIES OF GIVEN
    // CHARACTERISTICS (global/school, lowstakes/highstakes), ITERATING THROUGH
    // SEATS OF QUALIFIED LOBBY, AND ADDING PERSON TO LOBBY

    public static final int TABLE_SIZE = 8;
    private final static Logger logger = LoggerFactory.getLogger(PotManagerService.class);

    private final Map<School, List<Lobby>> universityLobbies; // Lobbies for a uni. Each school has a list of lobbies to it's name; can be joined as needed.
    private Map<School, Deque<ConnectedPlayer>> queuedPlayersForSchools;

    private final List<Lobby> globalLobbies; // All global lobbies; random joining is OK
    private Deque<ConnectedPlayer> queuedPlayers;

    public LobbyManagerService() {
        this.universityLobbies = new ConcurrentHashMap<>();
        this.globalLobbies = new ArrayList<>();
        this.queuedPlayers = new ArrayDeque<>();
        this.queuedPlayersForSchools = new ConcurrentHashMap<>();
    }

    // Persistent Queue Logic, probably should exist outside of lobby
    public ArrayList<ConnectedPlayer> getQueue(LobbyType lobbyType, boolean isLowStakes) {
        return new ArrayList<>(queuedPlayers);
    }


    // For global
    public void addPlayerToQueue(ConnectedPlayer player, boolean isLowStakes) {
        queuedPlayers.push(player);
    }

    // for if they r joining from a certin school
    public void addPlayerToQueueForSchool(School school, ConnectedPlayer player, boolean isLowStakes) {
        queuedPlayersForSchools.computeIfAbsent(school, _ -> {
            return new ArrayDeque<>();
        }).add(player);
    }

    // Fill lobby from queue. For global players.
    public boolean fillLobby(Lobby lobby, int num_to_insert) {
        // fill table with <num> players. fails if no players could be inserted OR if
        // table is full (check logs for more).
        // TODO: insert custom return value with more info??

        int tableCount = lobby.tableCount();

        if (tableCount == TABLE_SIZE) {
            // we alr have <num> capacity - cannot add any more.
            logger.warn("Lobby " + lobby.getId() + " is already at max capacity - cannot add " + num_to_insert + " players.");
            return false;
        }

        // insert them naively, from left to right. pop as many as possible.

        int insertionCount = 0;

        while (tableCount < TABLE_SIZE && insertionCount < num_to_insert) {
            ConnectedPlayer connectedPlayer;
            try {
                connectedPlayer = queuedPlayers.pop();
            } catch (NoSuchElementException e) {
                logger.warn("No queued players to pop for lobby" + lobby.getId() + "!");
                return insertionCount > 0 ? true : false;
            }

            // we know there is at least one empty slot; find as many as possible and put
            // all the players in.

            for (int i = 0; i < TABLE_SIZE; i++) {
                if (lobby.getPlayersAtTable()[i] == null) {
                    lobby.getPlayersAtTable()[i] = connectedPlayer;
                    insertionCount++;
                    tableCount++;
                }
            }
        }

        logger.info("Inserted " + insertionCount + " players into lobby " + lobby.getId());
        return true;
    }



    private int tableCount(Lobby lobby) {
        // number of players in the table
        int count = 0;
        for (ConnectedPlayer connectedPlayer : lobby.getPlayersAtTable()) {
            if (connectedPlayer != null)
                count++;
        }

        return count;
    }
}
