package com.collegeshowdown.poker_project.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

/**
 * Represents an active poker game with players, a deck, and a board.
 */
@Entity
public class Game implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private static Game instance;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id", nullable = false, updatable = false, unique = true)
    private int id;

    private String name;
    private int smallBlind;
    private int bigBlind;
    private int currentPot;

    @Transient // Don't persist deck to database
    private Deck deck;

    @Transient // We'll handle board separately
    private List<Card> board;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Player> players;

    @Transient // Don't persist winners to database
    private List<Winner> winners;

    @Transient // Don't persist currentPlayer to database
    private int currentPlayerIndex;

    /**
     * Default constructor for JPA
     */
    public Game() {
        instance = this;
        this.board = new ArrayList<>();
        this.players = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.deck = new Deck();
        this.smallBlind = 5;
        this.bigBlind = 10;
        this.currentPot = 0;
        this.currentPlayerIndex = 0;
    }

    /**
     * Get the singleton instance of the Game.
     * @return The game instance
     */
    public static Game getInstance() {
        if (instance == null) {
            instance = new Game();
        }
        return instance;
    }

    /**
     * Start playing the poker game.
     */
    public void play() {
        logger.info("Starting a new game with {} players", players.size());

        if (players.size() < 2) {
            logger.warn("Cannot start game with fewer than 2 players");
            return;
        }

        // Reset game state
        resetGame();

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
    private void resetGame() {
        logger.info("Resetting game state");
        this.deck = new Deck();
        this.board.clear();
        this.winners.clear();
        this.currentPot = 0;

        // Reset player cards
        for (Player player : players) {
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
        for (Player player : players) {
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
        Player player = players.stream()
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

    public List<Card> getBoard() {
        return board;
    }

    public void setBoard(List<Card> board) {
        this.board = board;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    // Add players
    public void addPlayer(Player player) {
        if (this.players.size() > 8) {
            logger.info("Cannot add player, lobby is full");
        }
        else ()
    }

    public List<Winner> getWinners() {
        return winners;
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

    public Player getCurrentPlayer() {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(currentPlayerIndex % players.size());
    }

    /**
     * Move to the next player.
     * @return The next player
     */
    public Player nextPlayer() {
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
                ", players=" + players.stream().map(Player::getName).collect(Collectors.toList()) +
                ", winners=" + winners +
                '}';
    }
}
