package com.collegeshowdown.poker_project.runtime.lobby;

import com.collegeshowdown.poker_project.runtime.card.Card;
import com.collegeshowdown.poker_project.runtime.card.Deck;
import com.collegeshowdown.poker_project.runtime.player.ConnectedPlayer;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.collegeshowdown.poker_project.model.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Collectors;
import java.util.Collections;

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

    private LobbyType lobbyType;

    private String associatedSchool; // make this a custom type later; we discuss.
    private Object customLobbyOptions; // define later what these may be.
    private String customLobbyCode;
    private String lobbyInfo;
    private String name;

    private Deque<ConnectedPlayer> queuedPlayers;
    private ConnectedPlayer activePlayers[] = new ConnectedPlayer[TABLE_SIZE]; // the table
    private List<ConnectedPlayer> winners;
    private int currentPlayerIndex;
    private ConnectedPlayer lastPlayerToBet;

    private List<Pot> allActivePots;
    private Pot currentPot = allActivePots.get(allActivePots.size() - 1);
    private boolean isLowStakes;
    private int smallBlind = isLowStakes ? 10 : 20;
    private int bigBlind = isLowStakes ? 20 : 50;
    private int smallBlindIndex;
    private int bigBlindIndex;

    private Deck deck;
    private List<Card> board;

    public Lobby() {
    }

    public List<Card> getBoard() {
        return this.board;
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
        this.smallBlindIndex = 0;
        this.bigBlindIndex = 1;
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
                if (activePlayers[i] == null) {
                    activePlayers[i] = connectedPlayer;
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
        for (ConnectedPlayer connectedPlayer : activePlayers) {
            if (connectedPlayer != null)
                count++;
        }

        return count;
    }

    public List<ConnectedPlayer> getWinners() {
        return winners;
    }

    /**
     * Start playing the poker game.
     */
    public void play() {
        logger.info("Starting a new game with {} players", activePlayers.length);

        if (tableCount() < 2) {
            logger.warn("Cannot start game with fewer than 2 players");
            return;
        }
        while (tableCount() >= 2) {
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
    }

    /**
     * Reset the game state for a new round.
     */
    private void resetLobby() {
        logger.info("Resetting game state");
        this.deck = new Deck();
        this.board.clear();
        this.winners.clear();
        this.allActivePots.clear();
        this.allActivePots.add(new Pot());

        // Reset player cards
        for (ConnectedPlayer connectedPlayer : activePlayers) {
            connectedPlayer.setCards(new ArrayList<>());
            connectedPlayer.setBestCards(new ArrayList<>());
            connectedPlayer.setHandRank(null);
        }
    }

    /**
     * Gets blinds out of two players.
     * Blinds start at indexes 0 and 1 when the lobby starts.
     * Each time a hand is played, the blind indices shift clockwise to the next
     * available ConnectedPlayer.
     * If a player does not have enough chips to cover the small or big blind, they
     * are all in,
     * and (later) a side pot should be created.
     */
    private void extractBlinds() {
        if (tableCount() < 2) {
            logger.warn("Not enough players to post blinds.");
            // Logic for awaiting new players or removing lobby after extended period of
            // time
            return;
        }

        // Determine the small blind player.
        int smallBlindSeat = getFirstValidIndex(smallBlindIndex);
        ConnectedPlayer smallBlindPlayer = activePlayers[smallBlindSeat];

        // Determine the big blind player.
        int bigBlindSeat = getFirstValidIndex(bigBlindIndex);
        // Ensure that the big blind seat is not the same as the small blind seat.
        if (bigBlindSeat == smallBlindSeat) {
            bigBlindSeat = getNextNonNullIndex(smallBlindSeat);
        }
        ConnectedPlayer bigBlindPlayer = activePlayers[bigBlindSeat];

        // Read the blind amounts.
        int sbRequired = smallBlind;
        int bbRequired = bigBlind;

        // Determine how much each player can contribute.
        int smallBlindChips = smallBlindPlayer.getActiveChips();
        int bigBlindChips = bigBlindPlayer.getActiveChips();

        // A player can only post as many chips as they have.
        int smallBlindContribution = Math.min(smallBlindChips, sbRequired);
        int bigBlindContribution = Math.min(bigBlindChips, bbRequired);

        // Deduct the chips from the players.
        smallBlindPlayer.betActiveChips(smallBlindContribution);
        bigBlindPlayer.betActiveChips(bigBlindContribution);

        // Add the contributions to the current pot.
        currentPot.setAmount(smallBlindContribution + bigBlindContribution);

        // Log cases where the player goes all in.
        if (smallBlindChips <= sbRequired) {
            logger.info("Small blind player {} is all in posting {} chips",
                    smallBlindPlayer.playerRecord.getId(), smallBlindContribution);
            // TODO: Create and manage side pot logic for the small blind.
        }
        if (bigBlindChips <= bbRequired) {
            logger.info("Big blind player {} is all in posting {} chips",
                    bigBlindPlayer.playerRecord.getId(), bigBlindContribution);
            // TODO: Create and manage side pot logic for the big blind.
        }

        logger.info("Blinds posted: Small Blind: {} (player {}) | Big Blind: {} (player {})",
                smallBlindContribution, smallBlindPlayer.playerRecord.getId(),
                bigBlindContribution, bigBlindPlayer.playerRecord.getId());

        // Update blind indices for the next hand.
        smallBlindIndex = getNextNonNullIndex(smallBlindSeat);
        bigBlindIndex = getNextNonNullIndex(smallBlindIndex);
    }

    /**
     * Returns the index of the first non-null player starting from the given index
     * (inclusive).
     */
    private int getFirstValidIndex(int startIndex) {
        int idx = startIndex % TABLE_SIZE;
        for (int i = 0; i < TABLE_SIZE; i++) {
            int candidate = (idx + i) % TABLE_SIZE;
            if (activePlayers[candidate] != null) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No valid player found in the lobby.");
    }

    /**
     * Returns the index of the next non-null player after the given index.
     */
    private int getNextNonNullIndex(int currentIndex) {
        int idx = (currentIndex + 1) % TABLE_SIZE;
        for (int i = 0; i < TABLE_SIZE; i++) {
            int candidate = (idx + i) % TABLE_SIZE;
            if (activePlayers[candidate] != null) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No next valid player found in the lobby.");
    }

    /**
     * Deal 2 hole cards to each player.
     */
    private void dealHoleCards() {
        logger.info("Dealing hole cards to {} players", activePlayers.length);
        for (int i = 0; i < 2; i++) {
            for (ConnectedPlayer connectedPlayer : activePlayers) {
                if (connectedPlayer != null) {
                    List<Card> holeCards = new ArrayList<>();
                    holeCards.add(deck.dealCard());
                    connectedPlayer.addCards(holeCards);
                }
            }
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
        winners = AnalysisEngine.getWinners(activePlayers);
        for (ConnectedPlayer handWinner : winners) {
            handWinner.winPot(this.currentPot);
            logger.info("Winners: {}", winners);
        }
    }

    /**
     * Process a bet from a player.
     *
     * @param playerId The ID of the player making the bet
     * @param amount   The bet amount
     * @return A message describing the result of the bet
     */

    public String processBet(int playerId, int amount) {
        // Find the player
        ConnectedPlayer player = Arrays.stream(activePlayers)
                .filter(p -> p.playerRecord.getId() == playerId)
                .findFirst()
                .orElse(null);

        if (player == null) {
            return "Player not found";
        }

        // Add the bet to the pot
        currentPot.setAmount(amount);
        player.betActiveChips(amount);
        lastPlayerToBet = player;

        return String.format("Player %s bet $%.2f. Current pot: $%d",
                player.playerRecord.getName(), amount, currentPot);
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public ConnectedPlayer[] getPlayers() {
        return activePlayers;
    }

    public void setPlayers(ConnectedPlayer[] activePlayers) {
        this.activePlayers = activePlayers;
    }

    // Add players
    public void addPlayer() {
        if (tableCount() > 8) {
            logger.info("Cannot add player, lobby is full");
        } else {
            for (int i = 0; i < this.activePlayers.length; i++) {
                if (activePlayers[i] == null) {
                    activePlayers[i] = queuedPlayers.pop();
                    break;
                }
            }
        }

        return;
    }

    public void setWinners(List<ConnectedPlayer> winners) {
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

    public Pot getCurrentPot() {
        return this.currentPot;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public ConnectedPlayer getCurrentPlayer() {
        if (tableCount() == 0) {
            return null;
        }
        return activePlayers[currentPlayerIndex % activePlayers.length];
    }

    /**
     * Move to the next player.
     *
     * @return The next player
     */
    public ConnectedPlayer nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % activePlayers.length;
        return getCurrentPlayer();
    }

    // Idk how to fix this LOL
    @Override
    public String toString() {
        return "Game{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", smallBlind=" + smallBlind +
                ", bigBlind=" + bigBlind +
                ", currentPot=" + currentPot +
                ", board=" + board +
                ", players=" + Arrays.stream(activePlayers)
                        .filter(Objects::nonNull)
                        .map(player -> player.playerRecord.getId())
                        .collect(Collectors.toList())
                +
                ", winners=" + winners +
                '}';
    }

}
