package com.collegeshowdown.poker_project.domain.lobby;

import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.models.*;
import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Deck;
import com.collegeshowdown.poker_project.domain.lobby.StakesLevel;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.UUID;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.Column;

// Note: @Id/@Column/@Transient below are vestigial — Lobby has no @Entity
// annotation and is never persisted via JPA (it's pure in-memory game
// state, owned entirely by LobbyManagerService/LobbyGameService). Left in
// place rather than stripped from every field; harmless since they're
// never processed without @Entity, just don't read anything into them.
public class Lobby { // POJO, LobbyService does the heavy liftng

    private final static Logger logger = LoggerFactory.getLogger(Lobby.class);

    public static final int TABLE_SIZE = 8;

    @Column(nullable = false)
    private final LobbyType lobbyType;

    @Id @Column(nullable = false)
    private String id = UUID.randomUUID().toString();

    @Column
    private String collegeDomain;

    @Transient
    private Deck deck = new Deck();

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

    @Column(nullable = false)
    private final int minimumBuyIn;

    @Transient
    private List<Card> board = new ArrayList<>();

    @Transient
    private GamePhase gamePhase = GamePhase.WAITING;

    @Transient
    private int currentBetAmount;

    @Transient
    private int lastAggressorIndex;

    /** Size of the most recent raise increment — used to enforce minimum re-raise. */
    @Transient
    private int lastRaiseSize;

    @Transient
    private Set<Integer> playersActedThisRound = new HashSet<>();

    /** Seats whose occupant disconnected while ALL_IN — vacated once the hand concludes. */
    @Transient
    private Set<Integer> pendingDepartureSeats = new HashSet<>();

    /** Total chips each player has put in across the entire hand (for side-pot calc). */
    @Transient
    private Map<ConnectedPlayer, Integer> handContributions = new HashMap<>();

    /** Chips each player has put in during the current betting round (for call-amount calc). */
    @Transient
    private Map<ConnectedPlayer, Integer> roundContributions = new HashMap<>();

    @Transient
    private int smallBlindSeat = -1;

    @Transient
    private int bigBlindSeat = -1;

    /** Epoch millis when the current turn timer expires; 0 when no timer is running. */
    @Transient
    private long turnDeadlineEpochMs = 0;

    /**
     * Bumped every time a turn timer is started or cancelled. A timer that had
     * already fired when it was cancelled may still be waiting on the lobby
     * lock — it compares against this and does nothing if it's stale.
     */
    @Transient
    private long turnTimerGeneration = 0;

    /** True when exactly two players were dealt into the current hand (heads-up action order). */
    @Transient
    private boolean headsUpHand;

    /**
     * Seats that already acted and then only faced an incomplete (short all-in)
     * raise — they may call or fold but not re-raise until a full raise
     * reopens the betting. Cleared on every full raise and every new street.
     */
    @Transient
    private Set<Integer> raiseClosedSeats = new HashSet<>();

    // Required for JPA
    protected Lobby() {
        this.lobbyType = null;
        this.smallBlind = 0;
        this.bigBlind = 0;
        this.minimumBuyIn = 0;
    }



    /** Global lobby with explicit stakes (1/2, 2/5, 5/10). */
    public Lobby(LobbyType type, StakesLevel stakes) {
        this.lobbyType = type;
        this.smallBlind = stakes.smallBlind;
        this.bigBlind = stakes.bigBlind;
        this.minimumBuyIn = stakes.minimumBuyIn;
        this.isLowStakes = stakes == StakesLevel.LOW;
    }



    /** School lobby with explicit stakes. collegeDomain stored for reference. */
    public Lobby(LobbyType type, StakesLevel stakes, String collegeDomain) {
        this(type, stakes);
        this.collegeDomain = collegeDomain;
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



    // Helper function determining which ConnectedPlayers are not null, and have
    // playerState ACTIVE (haven't
    // folded, and have an ActiveChip stack > 0)
    public List<ConnectedPlayer> getActivePlayersList() {
        return Arrays.stream(playersAtTable).filter(Objects::nonNull).filter(ConnectedPlayer::isActive)
                .collect(Collectors.toList());
    }



    public List<Integer> getActivePlayersListChipStacks() {
        return Arrays.stream(playersAtTable).filter(Objects::nonNull).filter(ConnectedPlayer::isActive)
                .map(ConnectedPlayer::getActiveChips).collect(Collectors.toList());
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



    public String getCollegeDomain() {
        return collegeDomain;
    }



    public void setCollegeDomain(String collegeDomain) {
        this.collegeDomain = collegeDomain;
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
        if (tableCount() == 0 || currentPlayerIndex < 0) {
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



    public Deck getDeck() { return deck; }

    public void resetDeck() { this.deck = new Deck(); }



    public boolean isLowStakes() {
        return isLowStakes;
    }



    public void setLowStakes(boolean isLowStakes) {
        this.isLowStakes = isLowStakes;
    }



    public GamePhase getGamePhase() {
        return gamePhase;
    }



    public void setGamePhase(GamePhase gamePhase) {
        this.gamePhase = gamePhase;
    }



    public int getCurrentBetAmount() {
        return currentBetAmount;
    }



    public void setCurrentBetAmount(int currentBetAmount) {
        this.currentBetAmount = currentBetAmount;
    }



    public int getLastAggressorIndex() {
        return lastAggressorIndex;
    }



    public void setLastAggressorIndex(int lastAggressorIndex) {
        this.lastAggressorIndex = lastAggressorIndex;
    }



    public int getLastRaiseSize() { return lastRaiseSize; }

    public void setLastRaiseSize(int size) { this.lastRaiseSize = size; }



    public Set<Integer> getPlayersActedThisRound() {
        return playersActedThisRound;
    }



    public void resetPlayersActedThisRound() {
        this.playersActedThisRound = new HashSet<>();
    }



    public Set<Integer> getPendingDepartureSeats() {
        return pendingDepartureSeats;
    }



    public Map<ConnectedPlayer, Integer> getHandContributions() {
        return Collections.unmodifiableMap(handContributions);
    }



    public void addHandContribution(ConnectedPlayer player, int amount) {
        handContributions.merge(player, amount, Integer::sum);
    }



    public void resetHandContributions() {
        handContributions = new HashMap<>();
    }



    public int getRoundContribution(ConnectedPlayer player) {
        return roundContributions.getOrDefault(player, 0);
    }



    public void addRoundContribution(ConnectedPlayer player, int amount) {
        roundContributions.merge(player, amount, Integer::sum);
    }



    public void resetRoundContributions() {
        roundContributions = new HashMap<>();
    }



    public int getSmallBlindSeat() { return smallBlindSeat; }
    public void setSmallBlindSeat(int seat) { this.smallBlindSeat = seat; }

    public int getBigBlindSeat() { return bigBlindSeat; }
    public void setBigBlindSeat(int seat) { this.bigBlindSeat = seat; }

    public long getTurnDeadlineEpochMs() { return turnDeadlineEpochMs; }
    public void setTurnDeadlineEpochMs(long ms) { this.turnDeadlineEpochMs = ms; }

    public long getTurnTimerGeneration() { return turnTimerGeneration; }
    public void bumpTurnTimerGeneration() { this.turnTimerGeneration++; }

    public boolean isHeadsUpHand() { return headsUpHand; }
    public void setHeadsUpHand(boolean headsUpHand) { this.headsUpHand = headsUpHand; }

    public Set<Integer> getRaiseClosedSeats() { return raiseClosedSeats; }



    @Override
    public String toString() {
        return "Lobby{" + "id=" + id + '\'' + ", smallBlind=" + smallBlind + ", bigBlind=" + bigBlind + ", activePots="
                + allActivePots.size() + ", board=" + board + ", players="
                + Arrays.stream(playersAtTable).filter(Objects::nonNull).map(player -> player.playerRecord.getId())
                        .collect(Collectors.toList())
                + ", winners=" + winners + '}';
    }
}