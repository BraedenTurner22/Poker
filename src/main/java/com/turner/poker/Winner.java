package com.turner.poker;

import java.util.SortedSet;

public class Winner {
    private String id;
    private HandRank handRank;
    private SortedSet<Card> winningCards;

    public Winner(String id, HandRank handRank, SortedSet<Card> winningCards) {
        this.id = id;
        this.handRank = handRank;
        this.winningCards = winningCards;
    }

    @Override
    public String toString() {
        return "Winner [id=" + id + ", handRank=" + handRank + ", winningCards=" + winningCards
                + "]";
    }


}
