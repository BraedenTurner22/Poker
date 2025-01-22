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
        StringBuilder builder = new StringBuilder();
        builder.append("Winner [");
        builder.append("id: " + id + ", ");
        builder.append("handRank: " + handRank + ", ");
        builder.append("card(s): ");
        // }
        int count = 0;
        for (Card card : bestCards) {
            count++;
            builder.append(card.toString());
            if (count < bestCards.size())
                builder.append(", ");
        }
        builder.append("]");
        return builder.toString();
    }
}
