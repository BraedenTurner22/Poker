package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

public class PlayerResult {
    Player player = null;
    private HandRank handRank;
    private List<Card> bestCards;

    public PlayerResult(Player player, HandRank handRank, List<Card> bestCards) {
        this.player = player;
        this.handRank = handRank;
        this.bestCards = bestCards;
    }

    public String getId() {
        return player.getId();
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Card> getAllCards() {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(player.getCards());
        allCards.addAll(Board.getCards());
        return allCards;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    @Override
    public String toString() {
        return "PlayerResult [player=" + player + ", handRank=" + handRank + ", allCards="
                + getAllCards() + ", bestCards=" + bestCards + "]";
    }
}
