package com.collegeshowdown.poker_project.runtime.player;

import com.collegeshowdown.poker_project.model.PlayerRecord;
import com.collegeshowdown.poker_project.runtime.card.*;
import com.collegeshowdown.poker_project.runtime.lobby.Pot;

import java.util.*;

public class ConnectedPlayer {
    public final PlayerRecord playerRecord;
    public final Object connection; // TODO - figure this out

    private List<Card> cards;
    private List<Card> bestCards;
    private int chipsActivelyUsed;
    private Stack<Pot> activePots;

    private HandRank handRank;

    public ConnectedPlayer(PlayerRecord playerRecord, Object connection) {
        this.playerRecord = playerRecord;
        this.connection = connection;
        this.cards = new ArrayList<>();
        this.bestCards = new ArrayList<>();
        this.activePots = new Stack<>();
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

    public void winPot(Pot pot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed + pot.getAmount();
    }

    public Stack<Pot> getActivePots() {
        return this.activePots;
    }

    public void foldCards() {
        this.cards.clear();
        this.activePots.clear();
        ;
    }

    // Set active pot?
}
