package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Board {

    private static List<Card> cards = new ArrayList<>();

    private Board() {}

    public static List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public static void burn() {
        Deck.getTopCard();
    }

    public static void dealCards(List<Card> cards) {
        Board.cards.addAll(cards);
    }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : cards)
            builder.append(card.toString() + "\n");
        return builder.toString();
    }
}
