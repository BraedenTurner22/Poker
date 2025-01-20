package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;

public class Deck {

    private ArrayList<Card> deck;

    public Deck() {
        deck = new ArrayList<Card>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
    }

    // public ArrayList<Card> getDeckCopy() {
    // ArrayList<Card> copy = new ArrayList<Card>();
    // Collections.copy(deck, copy);
    // return copy;
    // }

    public int getDeckSize() {
        return deck.size();
    }

    public void shuffle() {
        Collections.shuffle(deck);
    }

    public Card getTopCard() {
        Card topCard = deck.get(0);
        deck.remove(0);
        return topCard;
    }

    // public ArrayList<Card> initializeDeck() {
    // for (Suit suit : Suit.values()) {
    // for (Rank rank : Rank.values()) {
    // deck.add(new Card(suit, rank));
    // }
    // }
    // return deck;
    // }

}
