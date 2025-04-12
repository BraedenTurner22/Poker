package com.collegeshowdown.poker_project.runtime.card;

import com.collegeshowdown.poker_project.runtime.card.Rank;
import com.collegeshowdown.poker_project.runtime.card.Suit;

public class Card implements Comparable<Card> {
    private Rank rank;
    private Suit suit;

    public Card() {
    }

    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public void setSuit(Suit suit) {
        this.suit = suit;
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }

    @Override
    public int compareTo(Card other) {
        return Integer.compare(this.rank.getValue(), other.rank.getValue());
    }
}
