package com.collegeshowdown.poker_project.domain.player;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import com.collegeshowdown.poker_project.runtime.card.*;

import java.util.*;

public class ConnectedPlayer {
    public final PlayerRecord playerRecord;
    public final Object connection; // TODO - figure this out

    private List<Card> cards;
    private List<Card> bestCards;
    private int chipsActivelyUsed;

    private HandRank handRank;

    public enum PlayerState {
        ACTIVE, FOLDED, ALL_IN
    }

    public ConnectedPlayer(PlayerRecord playerRecord, Object connection) {
        this.playerRecord = playerRecord;
        this.connection = connection;
        this.cards = new ArrayList<>();
        this.bestCards = new ArrayList<>();
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



    public int payBlind(int blindAmount, Pot currentPot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed - blindAmount;
        currentPot.addToAmountToPot(blindAmount);
        currentPot.addPlayerContribution(this, blindAmount);
        return blindAmount;
    }



    public int call(int callAmount, Pot currentPot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed - callAmount;
        currentPot.addToAmountToPot(callAmount);
        currentPot.addPlayerContribution(this, callAmount);
        this.p
        return callAmount;
    }



    public int raise(int raiseAmount, Pot currentPot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed - raiseAmount;
        currentPot.addToAmountToPot(raiseAmount);
        currentPot.addPlayerContribution(this, raiseAmount);
        return raiseAmount;
    }



    public void foldCards(List<Pot> allActivePots) {
        setCards(new ArrayList<>());
        setBestCards(new ArrayList<>());
        setHandRank(null);

        for (Pot pot : allActivePots) {
            pot.getPlayersInPot().remove(this);
        }
    }



    public void winPot(Pot currentPot) {
        this.chipsActivelyUsed = this.chipsActivelyUsed + currentPot.getAmountInPot();
        currentPot.addToAmountToPot(chipsActivelyUsed);
    }



    public boolean isActive() {
        return !cards.isEmpty() && chipsActivelyUsed > 0;
    }

}