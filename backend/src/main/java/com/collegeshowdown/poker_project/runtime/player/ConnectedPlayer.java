package com.collegeshowdown.poker_project.runtime.player;

import com.collegeshowdown.poker_project.model.PlayerRecord;
import com.collegeshowdown.poker_project.runtime.card.*;

import java.util.*;

public class ConnectedPlayer {
    public final PlayerRecord playerRecord;
    public final Object connection; // TODO - figure this out

    private List<Card> cards;
    private List<Card> bestCards;
    private int chipsActivelyUsed;

    private HandRank handRank;

    public ConnectedPlayer(PlayerRecord playerRecord, Object connection) {
        this.playerRecord = playerRecord;
        this.connection = connection;
    }

    public void addCards(List<Card> cards) {
        for (Card card : cards) {
            this.cards.add(card);
        }
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public List<Card> getCards() {
        return this.cards;
    }

    public List<Card> getBestCards() {
        return this.bestCards;
    }

    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }

    public Card getBestCardAtIndex(int index) {
        return this.bestCards.get(index);
    }

    public HandRank getHandRank() {
        return this.handRank;
    }

    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }

    public int getActiveChips() {
        return this.chipsActivelyUsed;
    }

    public void betActiveChips(int bettingAmount) {
        chipsActivelyUsed = chipsActivelyUsed - bettingAmount;
    }

    public void winPot(int pot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed + pot;
    }

}
