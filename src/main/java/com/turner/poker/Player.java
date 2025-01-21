package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private String id;
    private List<Card> cards;
    private int tablePosition;
    private int chips;
    private boolean bigBlindPosition = false;
    private boolean smallBlindPosition = false;

    public Player(String id, int tablePosition, int chips) {
        this.id = id;
        this.tablePosition = tablePosition;
        this.chips = chips;
        cards = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void acceptCard(Card card) {
        cards.add(card);
    }

    public void discardCard(Card card) {
        cards.remove(card);
    }

    public void playBigBlind(int bigBlind) {
        chips -= bigBlind;
    }

    public void playSmallBlind(int smallBlind) {
        chips -= smallBlind;
    }

    public int getTablePosition() {
        return tablePosition;
    }

    public int getChips() {
        return chips;
    }

    public List<Card> removeCards() {
        List<Card> copy = new ArrayList<>();
        Collections.copy(cards, copy);
        cards.clear();
        return copy;
    }

    public List<Card> getCards() {
        List<Card> copy = new ArrayList<>();
        Collections.copy(cards, copy);
        return copy;
    }

    public void play() {}

    @Override
    public String toString() {
        return "Player [id=" + id + ", cards=" + cards + ", tablePosition=" + tablePosition + "]";
    }

    public void setBigBlindPosition(boolean value) {
        bigBlindPosition = value;
    }

    public void setSmallBlindPosition(boolean value) {
        smallBlindPosition = value;
    }
}
