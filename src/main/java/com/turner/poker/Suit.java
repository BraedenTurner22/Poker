package com.turner.poker;

public enum Suit {
    CLUBS(1), DIAMONDS(2), HEARTS(3), SPADES(4);

    private final int value;

    public int getValue() {
        return value;
    }

    Suit(int value) {
        this.value = value;
    }
}
