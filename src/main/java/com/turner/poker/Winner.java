package com.turner.poker;

import java.util.List;

public class Winner {
    private String id;
    private HandRank handRank;
    private List<Card> winningCards;

    public Winner(String id, HandRank handRank, List<Card> winningCards) {
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
