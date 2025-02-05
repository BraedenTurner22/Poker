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

    public HandRank getWinnerHandRank() {
        return this.handRank;
    }

    public List<Card> getWinningCards() {
        return this.winningCards;
    }

    public Card getWinningCardAtIndex(int index) {
        return this.winningCards.get(index);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\nWinner...");
        stringBuilder.append("\n...id: " + id);
        stringBuilder.append("\n...handRank: " + handRank);
        stringBuilder.append("\n...winningCards: " + winningCards);
        return stringBuilder.toString();
    }
}
