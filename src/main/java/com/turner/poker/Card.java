package com.turner.poker;

public class Card {
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return "Card [suit=" + suit + ", rank=" + rank + "]";
    }

    // public static void main(String[] args) {
    // Card card = new Card(Suit.CLUBS, Rank.ACE);
    // System.out.println(card);
    // }
}
