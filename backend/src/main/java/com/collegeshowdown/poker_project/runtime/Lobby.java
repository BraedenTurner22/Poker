package com.collegeshowdown.poker_project.runtime;
import com.collegeshowdown.poker_project.runtime.card.Card;
import com.collegeshowdown.poker_project.runtime.card.Deck;
import com.collegeshowdown.poker_project.runtime.player.ConnectedPlayer;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.collegeshowdown.poker_project.model.*;
import com.collegeshowdown.poker_project.model.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.stream.Collectors;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.io.Serializable;

// NON PERSISTENT !!!!!!
public class Lobby {
    public static final int TABLE_SIZE = 8;

    enum LobbyType {
        UNIVERSITY,
        GLOBAL,
    }

    private final static Logger logger = LoggerFactory.getLogger(PlayerRecord.class);

    private String id = UUID.randomUUID().toString();

    private static Lobby instance;
    private LobbyType lobbyType;
    private String associatedSchool; // make this a custom type later; we discuss.
    private Object customLobbyOptions; // define later what these may be.
    private String customLobbyCode;
    private String lobbyInfo;
    private Deque<ConnectedPlayer> queuedPlayers;
    private ConnectedPlayer activePlayers[] = new ConnectedPlayer[TABLE_SIZE]; // the table
    private Deck deck;
    private String name;
    private boolean isLowStakes;
    private int smallBlind = isLowStakes ? 10 : 20;
    private int bigBlind = isLowStakes ? 20 : 50;
    private int currentPot;
    private List<Card> board;
    private List<Winner> winners;
    private int currentPlayerIndex;

    public Lobby() {
    }

    /**
     * Get the singleton instance of the Game.
     *
     * @return The game instance
     */
    public static Lobby getInstance() {
        if (instance == null) {
            instance = new Lobby();
        }
        return instance;
    }

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

    public ArrayList<Winner> getWinners() {
        return new ArrayList<>();
    }



    /**
     * Start playing the poker game.
     */
    public void play() {
        logger.info("Starting a new game with {} players", activePlayers.length);

        if (activePlayers.length < 2) {
            logger.warn("Cannot start game with fewer than 2 players");
            return;
        }

        // Reset lobby state
        resetLobby();

        // Deal 2 cards to each player
        dealHoleCards();

        // Deal the flop (3 cards)
        dealFlop();

        // Deal the turn (1 card)
        dealTurn();

        // Deal the river (1 card)
        dealRiver();

        // Determine the winners
        determineWinners();
    }

    /**
     * Reset the game state for a new round.
     */
    private void resetLobby() {
        logger.info("Resetting game state");
        this.deck = new Deck();
        this.board.clear();
        this.winners.clear();
        this.currentPot = 0;

        // Reset player cards
        for (ConnectedPlayer player : activePlayers) {
            player.setCards(new ArrayList<>());
            player.setBestCards(new ArrayList<>());
            player.setHandRank(null);
        }
    }

    /**
     * Deal 2 hole cards to each player.
     */
    private void dealHoleCards() {
        logger.info("Dealing hole cards to {} players", players.size());
        for (PlayerRecord player : players) {
            List<Card> holeCards = new ArrayList<>();
            holeCards.add(deck.dealCard());
            holeCards.add(deck.dealCard());
            player.setCards(holeCards);
        }
    }

    /**
     * Deal the flop (first 3 community cards).
     */
    private void dealFlop() {
        logger.info("Dealing the flop");
        // Burn a card
        deck.dealCard();

        // Deal 3 cards to the board
        for (int i = 0; i < 3; i++) {
            board.add(deck.dealCard());
        }
    }

    /**
     * Deal the turn (4th community card).
     */
    private void dealTurn() {
        logger.info("Dealing the turn");
        // Burn a card
        deck.dealCard();

        // Deal 1 card to the board
        board.add(deck.dealCard());
    }

    /**
     * Deal the river (5th community card).
     */
    private void dealRiver() {
        logger.info("Dealing the river");
        // Burn a card
        deck.dealCard();

        // Deal 1 card to the board
        board.add(deck.dealCard());
    }

    /**
     * Determine the winners of the current game.
     */
    private void determineWinners() {
        logger.info("Determining winners");
        winners = AnalysisEngine.getWinners(players);
        logger.info("Winners: {}", winners);
    }

    /**
     * Process a bet from a player.
     *
     * @param playerId The ID of the player making the bet
     * @param amount   The bet amount
     * @return A message describing the result of the bet
     */
    public String processBet(int playerId, double amount) {
        // Find the player
        PlayerRecord player = players.stream()
                .filter(p -> p.getId() == playerId)
                .findFirst()
                .orElse(null);

        if (player == null) {
            return "Player not found";
        }

        // Add the bet to the pot
        currentPot += amount;

        return String.format("Player %s bet $%.2f. Current pot: $%d",
                player.getName(), amount, currentPot);
    }

    // Getters and setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Deck getDeck() {
        return deck;
    }

    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    public void setBoard(List<Card> board) {
        this.board = board;
    }

    public List<PlayerRecord> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerRecord> players) {
        this.players = players;
    }

    // Add players
    public void addPlayer(PlayerRecord player) {
        if (this.players.size() > 8) {
            logger.info("Cannot add player, lobby is full");
        } else {
            this.players.add(player);
        }

        return;
    }

    public void setWinners(List<Winner> winners) {
        this.winners = winners;
    }

    public int getSmallBlind() {
        return smallBlind;
    }

    public void setSmallBlind(int smallBlind) {
        this.smallBlind = smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public void setBigBlind(int bigBlind) {
        this.bigBlind = bigBlind;
    }

    public int getCurrentPot() {
        return currentPot;
    }

    public void setCurrentPot(int currentPot) {
        this.currentPot = currentPot;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public PlayerRecord getCurrentPlayer() {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(currentPlayerIndex % players.size());
    }

    /**
     * Move to the next player.
     *
     * @return The next player
     */
    public PlayerRecord nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        return getCurrentPlayer();
    }

    @Override
    public String toString() {
        return "Game{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", smallBlind=" + smallBlind +
                ", bigBlind=" + bigBlind +
                ", currentPot=" + currentPot +
                ", board=" + board +
                ", players=" + players.stream().map(PlayerRecord::getName).collect(Collectors.toList()) +
                ", winners=" + winners +
                '}';
    }
}