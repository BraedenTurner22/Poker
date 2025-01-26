package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private static List<Card> deck = new ArrayList<Card>();

    static {
        reset();
    }

    protected Deck() {}

    public static void reset() {
        deck.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(rank, suit));
            }
        }
    }

    public static List<Card> getDeck() {
        return Collections.unmodifiableList(deck);
    }

    public static int getDeckSize() {
        return deck.size();
    }

    public static void shuffle() {
        List<Card> list = new ArrayList<>(deck);
        // Shuffle the List
        Collections.shuffle(list);
        deck.clear();
        deck.addAll(list);
    }

    public static Card getTopCard() {
        return deck.get(0);
    }

    // static void dealCardsToPlayers(int count) {
    // for (int i = 0; i < count; i++) {
    // for (Player player : Players.getPlayers().values()) {
    // player.acceptCard(Deck.getTopCard());
    // }
    // }
    // }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : deck)
            builder.append(card.toString() + "\n");
        return builder.toString();
    }
}
