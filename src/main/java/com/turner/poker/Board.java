package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Board {

    private static List<Card> cards = new ArrayList<>();

    private Board() {}

    public static void reset() {
        cards.clear();
    }

    public static List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public static void burn() {
        Deck.getTopCard();
    }

    public static void layoutCards(List<Card> cards) {
        Board.cards.addAll(cards);
    }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : cards)
            builder.append(card.toString() + "\n");

        // int count = 0;
        // for (Card card : cards) {
        // count++;
        // builder.append(card.toString());
        // if (count < cards.size())
        // builder.append(", ");
        // }
        return builder.toString();
    }
}
