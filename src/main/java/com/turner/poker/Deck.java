package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private static List<Card> deck = new ArrayList<Card>();

    static {
        reset();
    }

    private Deck() {}

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

    public static void dealTestCardsToPlayers(List<Card> testCards) {
        // for (int i = 0; i < count; i++) {
        // for (Player player : Players.getPlayers()) {
        // player.acceptCard(Deck.getTopCard());
        // }
        // }
        Player player = Players.getPlayer(0);
        player.acceptCard(testCards.get(0));
        player.acceptCard(testCards.get(1));
        player = Players.getPlayer(1);
        player.acceptCard(testCards.get(2));
        player.acceptCard(testCards.get(3));

    }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : deck)
            builder.append(card.toString() + "\n");
        return builder.toString();
    }
}
