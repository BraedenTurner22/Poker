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

    public static void dealCards(List<Card> cards) {
        Board.cards.addAll(cards);
    }

    public static void printBoard() {
        for (Card card : cards)
            System.out.println(card);
    }
}
