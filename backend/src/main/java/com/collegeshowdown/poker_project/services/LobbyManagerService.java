package com.collegeshowdown.poker_project.services;

import java.util.ArrayList;
import java.util.Deque;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.collegeshowdown.poker_project.domain.lobby.*;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;

@Service
public class LobbyManagerService {

    // NEED LOGIC FOR ITERATING THROUGH ALL QUALIFIED LOBBIES OF GIVEN
    // CHARACTERISTICS (global/school, lowstakes/highstakes), ITERATING THROUGH
    // SEATS OF QUALIFIED LOBBY, AND ADDING PERSON TO LOBBY

    private Deque<ConnectedPlayer> queuedPlayers;

    // Persistent Queue Logic, probably should exist outside of lobby
    public ArrayList<ConnectedPlayer> getQueue(LobbyType lobbyType, boolean isLowStakes) {
        return new ArrayList<>(queuedPlayers);
    }



    public void addPlayerToQueue(ConnectedPlayer player, LobbyType lobbyType, boolean isLowStakes) {
        queuedPlayers.push(player);
    }



    // Fill lobby from queue
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
            ConnectedPlayer connectedPlayer;
            try {
                connectedPlayer = queuedPlayers.pop();
            } catch (NoSuchElementException e) {
                logger.warn("No queued players to pop for lobby" + id + "!");
                return insertionCount > 0 ? true : false;
            }

            // we know there is at least one empty slot; find as many as possible and put
            // all the players in.

            for (int i = 0; i < TABLE_SIZE; i++) {
                if (playersAtTable[i] == null) {
                    playersAtTable[i] = connectedPlayer;
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
        for (ConnectedPlayer connectedPlayer : playersAtTable) {
            if (connectedPlayer != null)
                count++;
        }

        return count;
    }
}
