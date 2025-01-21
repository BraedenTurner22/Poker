package com.turner.poker;

import java.util.List;

public class Winner {
    private String id;
    private HandRank handRank;
    private List<Card> bestCards;

    public Winner(String id, HandRank handRank, List<Card> bestCards) {
        this.id = id;
        this.handRank = handRank;
        this.bestCards = bestCards;
    }

    public String getId() {
        return id;
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    @Override
    public String toString() {
        return "Winner [id=" + id + ", handRank=" + handRank + ", bestCards=" + bestCards + "]";
    }
}
