package com.collegeshowdown.poker_project.domain.player;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.models.PlayerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Always constructed with {@code new} (see LobbyManagerService.joinLobby) — not a Spring bean. */
public class ConnectedPlayer {

    public final PlayerRecord playerRecord;

    private List<Card> cards = new ArrayList<>();
    private List<Card> bestCards = new ArrayList<>();
    private int chipsActivelyUsed;
    private PlayerState playerState = PlayerState.ACTIVE;
    private HandRank handRank;

    public ConnectedPlayer(PlayerRecord playerRecord) {
        this.playerRecord = playerRecord;
    }

    // ─── State accessors ─────────────────────────────────────────────────────────



    public PlayerState getPlayerState() {
        return playerState;
    }



    public void setPlayerState(PlayerState playerState) {
        this.playerState = playerState;
    }



    public boolean isActive() {
        return this.playerState == PlayerState.ACTIVE;
    }

    // ─── Card methods ────────────────────────────────────────────────────────────



    public void addCards(List<Card> cards) {
        this.cards.addAll(cards);
    }



    public void setCards(List<Card> cards) {
        this.cards = cards;
    }



    public List<Card> getCards() {
        return cards;
    }



    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }



    public List<Card> getBestCards() {
        return bestCards;
    }



    public Card getBestCardAtIndex(int index) {
        return bestCards.get(index);
    }

    // ─── Hand rank ───────────────────────────────────────────────────────────────



    public HandRank getHandRank() {
        return handRank;
    }



    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }

    // ─── Betting actions ─────────────────────────────────────────────────────────



    /** Returns the amount actually paid (may be less than requested if short-stacked). */
    public int payBlind(int blindAmount, Pot workingPot) {
        int actual = deductChips(blindAmount, workingPot);
        checkAllIn();
        return actual;
    }



    /** Returns the amount actually called. */
    public int call(int callAmount, Pot workingPot) {
        int actual = deductChips(callAmount, workingPot);
        checkAllIn();
        return actual;
    }



    /** Returns the total amount actually put in. */
    public int raise(int totalAmount, Pot workingPot) {
        int actual = deductChips(totalAmount, workingPot);
        checkAllIn();
        return actual;
    }



    /** Deducts chips, adds to working pot, returns actual amount moved. */
    private int deductChips(int requested, Pot pot) {
        int actual = Math.min(requested, this.chipsActivelyUsed);
        this.chipsActivelyUsed -= actual;
        pot.addToAmountToPot(actual);
        return actual;
    }



    private void checkAllIn() {
        if (chipsActivelyUsed <= 0) {
            chipsActivelyUsed = 0;
            this.playerState = PlayerState.ALL_IN;
        }
    }

    // ─── Folding & winning ───────────────────────────────────────────────────────



    public void foldCards(List<Pot> allActivePots) {
        this.playerState = PlayerState.FOLDED;
        this.cards.clear();
        this.bestCards.clear();
        this.handRank = null;

        for (Pot pot : allActivePots) {
            pot.getPlayersInPot().remove(this);
        }
    }



    public void buyIn(int amount) {
        this.chipsActivelyUsed = amount;
    }

    public void winPot(Pot currentPot) {
        int winnings = currentPot.getAmountInPot();
        this.chipsActivelyUsed += winnings;
        currentPot.emptyPot();
    }

    // ─── Chips getter ───────────────────────────────────────────────────────────



    public int getActiveChips() {
        return chipsActivelyUsed;
    }



    public void addChips(int amount) {
        this.chipsActivelyUsed += amount;
        if (this.chipsActivelyUsed > 0 && this.playerState == PlayerState.ALL_IN) {
            this.playerState = PlayerState.ACTIVE;
        }
    }

    // ─── Identity ────────────────────────────────────────────────────────────────
    // A ConnectedPlayer is constructed exactly once per seating and reused for
    // the lifetime of that seat (including reconnects), so reference identity
    // has always been sufficient in practice. Defined explicitly by player ID
    // anyway so pot membership / contribution maps / seat lookups stay correct
    // even if that ever changes.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectedPlayer other)) return false;
        return Objects.equals(playerRecord.getId(), other.playerRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(playerRecord.getId());
    }
}
