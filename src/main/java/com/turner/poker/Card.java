package com.turner.poker;

public class Card implements Comparable<Card> {
    private Suit suit = null;
    private Rank rank = null;

    protected Card() {}

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

    // Override compareTo
    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.rank.getValue(), other.rank.getValue());
    }

}
