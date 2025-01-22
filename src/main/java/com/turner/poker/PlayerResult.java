package com.turner.poker;

import java.util.List;

public class PlayerResult {
    private String id;
    private HandRank handRank;
    private List<Card> allCards;
    private List<Card> bestCards;

    public PlayerResult(String id, HandRank handRank) {
        this.id = id;
        this.handRank = handRank;
    }

    public PlayerResult(String id, HandRank handRank, List<Card> allCards, List<Card> bestCards) {
        this.id = id;
        this.handRank = handRank;
        this.allCards = allCards;
        this.bestCards = bestCards;
    }

    public String getId() {
        return id;
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Card> getAllCards() {
        return allCards;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    @Override
    public String toString() {
        return "PlayerResult [id=" + id + ", handRank=" + handRank + ", allCards=" + allCards
                + ", bestCards=" + bestCards + "]";
    }


}
