package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private String id;
    private List<Card> cards;
    private int tablePosition;
    // private int chips;
    // private boolean bigBlindPosition = false;
    // private boolean smallBlindPosition = false;
    private PlayerResult playerResult = null;

    public Player(String id, int tablePosition, int chips) {
        this.id = id;
        this.tablePosition = tablePosition;
        // this.chips = chips;
        cards = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void acceptCard(Card card) {
        cards.add(card);
    }

    // public void discardCard(Card card) {
    // cards.remove(card);
    // }

    // public void playBigBlind(int bigBlind) {
    // chips -= bigBlind;
    // }

    // public void playSmallBlind(int smallBlind) {
    // chips -= smallBlind;
    // }

    // public int getTablePosition() {
    // return tablePosition;
    // }

    // public int getChips() {
    // return chips;
    // }

    // public List<Card> removeCards() {
    // List<Card> copy = new ArrayList<>();
    // Collections.copy(cards, copy);
    // cards.clear();
    // return copy;
    // }

    public List<Card> getCards() {
        // return Collections.unmodifiableList(cards);
        return cards;
    }

    public void play() {}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Player [");
        builder.append("id: " + id + ", ");
        builder.append("tablePosition: " + tablePosition + ", ");
        builder.append("cards: ");
        int count = 0;
        for (Card card : cards) {
            count++;
            builder.append(card.toString());
            if (count < cards.size())
                builder.append(", ");
        }
        builder.append("]");
        return builder.toString();
    }

    // public void setBigBlindPosition(boolean value) {
    // bigBlindPosition = value;
    // }

    // public void setSmallBlindPosition(boolean value) {
    // smallBlindPosition = value;
    // }

    public void setPlayerResult(PlayerResult playerResult) {
        this.playerResult = playerResult;
    }

    public PlayerResult getPlayerResults() {
        return playerResult;
    }
}
