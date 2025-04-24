package com.collegeshowdown.poker_project.domain.card;

import java.util.*;
import java.security.SecureRandom;

public class Deck {
    Stack<Card> cards;
    private static final SecureRandom secureRandom = new SecureRandom();

    public Deck() {
        this.cards = new Stack<Card>();

        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                this.cards.push(new Card(rank, suit));
            }
        }

        shuffle();
    }



    public void shuffle() {
        Collections.shuffle(cards, secureRandom);
    }



    public Card dealCard() {
        return cards.pop();
    }



    public void burn() {
        cards.pop();
    }
}
