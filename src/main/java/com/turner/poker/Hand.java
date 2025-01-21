package com.turner.poker;

import java.util.List;

public class Hand {
    private List<Card> allCards;
    private HandRank handRank;
    private List<Card> bestCards;
    // private boolean bestHandOnBoard = false;

    public Hand(List<Card> allCards, HandRank handRank, List<Card> bestCards) {
        this.allCards = allCards;
        this.handRank = handRank;
        this.bestCards = bestCards;
    }

    public List<Card> getAllCards() {
        return allCards;
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }


    // public boolean isBestHandOnBoard() {
    // return bestHandOnBoard;
    // }

    // @Override
    // public String toString() {
    // return "Hand [cards=" + cards + ", handRank=" + handRank + ", bestCards=" + bestCards
    // + ", bestHandOnBoard=" + bestHandOnBoard + "]";
    // }

    @Override
    public String toString() {
        return "Hand [allCards=" + allCards + ", handRank=" + handRank + ", bestCards=" + bestCards
                + "]";
    }

}
