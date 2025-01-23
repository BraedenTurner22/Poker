package com.turner.poker;

public class Card {
    private final Suit suit;
    private final Rank rank;

    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(rank + " of " + suit);
        return builder.toString();
    }
}
