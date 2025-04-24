package com.collegeshowdown.poker_project.domain.lobby;

import com.collegeshowdown.poker_project.domain.analysis.AnalysisEngine;
import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Deck;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.models.*;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.LinkedHashMap;
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
        UNIVERSITY, GLOBAL,
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

    private ConnectedPlayer playersAtTable[] = new ConnectedPlayer[TABLE_SIZE]; // the table

    private List<ConnectedPlayer> winners;

    private int currentPlayerIndex;

    private List<Pot> allActivePots = new ArrayList<>();

    private Pot currentPot = allActivePots.get(allActivePots.size() - 1);

    private boolean isLowStakes;

    private final int smallBlind = isLowStakes ? 10 : 20;

    private final int bigBlind = isLowStakes ? 20 : 50;

    private int smallBlindIndex = getFirstValidIndex(0);

    private int bigBlindIndex = getNextValidIndex(1);

    private Deck deck = new Deck();

    private List<Card> board = new ArrayList<>();

    // Constructors
    public Lobby() {
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



    // Persistent Queue Logic, probably should exist outside of lobby
    public ArrayList<ConnectedPlayer> getQueue() {
        return new ArrayList<>(queuedPlayers);
    }



    public void addPlayerToQueue(ConnectedPlayer player) {
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



    /**
     * Start playing the poker game.
     */
    public void play() {
        logger.info("Starting a new game with {} players", playersAtTable.length);

        if (tableCount() < 2) {
            logger.warn("Cannot start game with fewer than 2 players");
            return;
        }
        while (tableCount() >= 2) {
            // Reset lobby state
            resetLobby();

            // Extract Blinds
            extractBlinds();

            // Initiate Betting

            // Deal 2 cards to each player
            dealHoleCards();

            // Initiate Betting

            // Deal the flop (3 cards)
            dealFlop();

            // Initiate Betting

            // Deal the turn (1 card)
            dealTurn();

            // Initiate Betting

            // Deal the river (1 card)
            dealRiver();

            // Initiate Betting

            // Determine the winners
            determineWinners();

            // advance blinds for next hand
            smallBlindIndex += 1;
            bigBlindIndex += 1;
        }
    }



    /**
     * Reset the game state for a new round.
     */
    private void resetLobby() {
        logger.info("Resetting game state");

        // reset shared state
        this.deck = new Deck();
        this.board.clear();
        this.winners.clear();
        this.allActivePots.clear();

        // clear each player's hand data
        for (ConnectedPlayer player : getActivePlayersList()) {
            player.getCards().clear();
            player.getBestCards().clear();
            player.setHandRank(null);
        }
    }



    // Helper function determining which ConnectedPlayers are not null, haven't
    // folded, and have an ActiveChip stack > 0
    private List<ConnectedPlayer> getActivePlayersList() {
        return Arrays.stream(playersAtTable).filter(Objects::nonNull).filter(ConnectedPlayer::isActive)
                .collect(Collectors.toList());
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
    private void extractBlinds() {

        // build fresh list of in‑hand players
        List<ConnectedPlayer> AllActivePlayersAfterReset = getActivePlayersList();

        // kick off the first pot at the min‐stack
        int leastChipCountOfInitialPlayer = minimumChipCountAmong(AllActivePlayersAfterReset);
        Pot firstPot = new Pot(bigBlind, leastChipCountOfInitialPlayer, AllActivePlayersAfterReset);
        this.allActivePots.add(firstPot);

        // 2) find blind seats
        int sbSeat = getFirstValidIndex(smallBlindIndex);
        int bbSeat = getFirstValidIndex(bigBlindIndex);
        if (bbSeat == sbSeat) {
            bbSeat = getNextValidIndex(sbSeat);
        }
        ConnectedPlayer sbPlayer = playersAtTable[sbSeat];
        ConnectedPlayer bbPlayer = playersAtTable[bbSeat];

        // Contribute small blind (all‑in if < blind)
        // Creates new pot
        sbPlayer.payBlind(smallBlind, currentPot);

        int bbContrib = Math.min(bbPlayer.getActiveChips(), bigBlind);
        if (bbContrib < bigBlind) {
            bbPlayer.payBlind(smallBlind, currentPot);
            int chipsOfPlayerWithMinStack = minimumChipCountAmong(getActivePlayersList());
            Pot splitBlindPot = new Pot(bigBlind - smallBlind, chipsOfPlayerWithMinStack, getActivePlayersList());
            this.allActivePots.add(splitBlindPot);
        } else {
            bbPlayer.payBlind(bigBlind, currentPot);
        }

        logger.info("Blinds posted: SB={} by {}, BB={} by {} → Pots: {}", smallBlind, sbPlayer.playerRecord.getId(),
                bbContrib, bbPlayer.playerRecord.getId(), allActivePots.size());
    }



    // Helper function finding smalelst active-chips among a list
    private int minimumChipCountAmong(List<ConnectedPlayer> players) {
        return players.stream().mapToInt(ConnectedPlayer::getActiveChips).min().orElse(0);
    }



    /**
     * Returns the index of the first non-null player starting from the given index
     * (inclusive).
     */
    private int getFirstValidIndex(int startIndex) {
        int idx = startIndex % TABLE_SIZE;
        for (int i = 0; i < TABLE_SIZE; i++) {
            int candidate = (idx + i) % TABLE_SIZE;
            if ((playersAtTable[candidate] != null) && (playersAtTable[candidate].getActiveChips() >= smallBlind)) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No valid player found in the lobby.");
    }



    /**
     * Returns the index of the next non-null player after the given index.
     */
    private int getNextValidIndex(int currentIndex) {
        int idx = (currentIndex + 1) % TABLE_SIZE;
        for (int i = 0; i < TABLE_SIZE; i++) {
            int candidate = (idx + i) % TABLE_SIZE;
            if ((playersAtTable[candidate] != null) && (playersAtTable[candidate].getActiveChips() > smallBlind)) {
                return candidate;
            }
        }
        throw new NoSuchElementException("No next valid player found in the lobby.");
    }



    /**
     * Deal 2 hole cards to each player.
     */
    private void dealHoleCards() {
        logger.info("Dealing hole cards to {} players", playersAtTable.length);
        for (int i = 0; i < 2; i++) {
            for (ConnectedPlayer connectedPlayer : playersAtTable) {
                if (connectedPlayer.isActive()) {
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
        winners = AnalysisEngine.getWinners(playersAtTable);
        for (ConnectedPlayer handWinner : winners) {
            handWinner.winPot(this.currentPot);
            logger.info("Winners: {}", winners);
        }
    }

    // Regular Getters and setters



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



    // Add players
    public void addPlayer() {
        if (tableCount() > 8) {
            logger.info("Cannot add player, lobby is full");
        } else {
            for (int i = 0; i < this.playersAtTable.length; i++) {
                if (playersAtTable[i] == null) {
                    playersAtTable[i] = queuedPlayers.pop();
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



    public Pot getCurrentPot() {
        return this.currentPot;
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



    /**
     * Move to the next player.
     *
     * @return The next player
     */
    public ConnectedPlayer nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % playersAtTable.length;
        return getCurrentPlayer();
    }



    // Idk how to fix this LOL
    @Override
    public String toString() {
        return "Game{" + "id=" + id + ", name='" + name + '\'' + ", smallBlind=" + smallBlind + ", bigBlind=" + bigBlind
                + ", currentPot=" + currentPot + ", board=" + board + ", players="
                + Arrays.stream(playersAtTable).filter(Objects::nonNull).map(player -> player.playerRecord.getId())
                        .collect(Collectors.toList())
                + ", winners=" + winners + '}';
    }

}
