package com.collegeshowdown.poker_project.runtime.card;

import java.util.*;

public class Deck {
    Stack<Card> cards;

    public Deck() {
        this.cards = new Stack<Card>();

        for (Suit suit: Suit.values()) {
            for (Rank rank: Rank.values()) {
                this.cards.push(new Card(rank, suit));
            }
        }

        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card dealCard() {
        return cards.pop();
    }

    public void burn() {
        cards.pop();
    }
}
