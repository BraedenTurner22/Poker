package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Board {
    private final static Logger logger = LoggerFactory.getLogger(Player.class);

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
        logger.info("Board: layoutCards: cards: " + cards);
        Board.cards.addAll(cards);
        for (Player player : Players.getPlayers().values()) {
            logger.info("Board: layoutCards: player: " + player);
            for (Card card : cards)
                player.acceptCard(card);
        }
    }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Card card : cards)
            builder.append("\n" + card.toString());
        builder.append("\n");
        return builder.toString();
    }

}
