package com.turner.poker;

public class Winner {
    private String id;
    private HandRank handRank;

    public Winner(String id, HandRank handRank) {
        this.id = id;
        this.handRank = handRank;
    }

    public String getId() {
        return id;
    }

    public HandRank getHandRank() {
        return handRank;
    }

    @Override
    public String toString() {
        return "Winner [id=" + id + ", handRank=" + handRank + "]";
    }
}
