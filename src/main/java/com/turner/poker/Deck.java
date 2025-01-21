package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private static List<Card> deck = new ArrayList<Card>();

    static {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
    }

    private Deck() {}

    public static int getDeckSize() {
        return deck.size();
    }

    public static void shuffle() {
        Collections.shuffle(deck);
    }

    public static Card getTopCard() {
        Card topCard = deck.get(0);
        deck.remove(0);
        return topCard;
    }

    public static void dealCardsToPlayers(int count) {
        for (int i = 0; i < count; i++) {
            for (Player player : Players.getPlayers()) {
                player.acceptCard(Deck.getTopCard());
            }
        }
    }

    // public static void printDeck() {
    // for (Card card : deck)
    // System.out.println(card + ", ");
    // }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : deck)
            builder.append(card.toString() + "\n");
        return builder.toString();
    }
}
