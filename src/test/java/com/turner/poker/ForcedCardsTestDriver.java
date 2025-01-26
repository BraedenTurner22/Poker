package com.turner.poker;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForcedCardsTestDriver {

    private final static Logger logger = LoggerFactory.getLogger(ForcedCardsTestDriver.class);

    public static void main(String[] args) {
        Players.addPlayer(new Player(Integer.toString(0), 100));

        Players.getPlayer(Integer.toString(0)).acceptCard(new Card(Rank.SEVEN, Suit.DIAMONDS));
        Players.getPlayer(Integer.toString(0)).acceptCard(new Card(Rank.SEVEN, Suit.HEARTS));
        // Players.getPlayer(Integer.toString(0)).acceptCard(new Card(Rank.FIVE, Suit.CLUBS));

        List<Card> boardCards = new ArrayList<>();
        boardCards.add(new Card(Rank.SEVEN, Suit.CLUBS));
        boardCards.add(new Card(Rank.TEN, Suit.HEARTS));
        boardCards.add(new Card(Rank.TEN, Suit.DIAMONDS));
        boardCards.add(new Card(Rank.EIGHT, Suit.DIAMONDS));
        boardCards.add(new Card(Rank.FIVE, Suit.DIAMONDS));
        Board.layoutCards(boardCards);

        logger.info("---Board---");
        logger.info(Board.staticToString());

        logger.info("---Players---");
        logger.info(Players.staticToString());

        List<Winner> winners = Game.getWinners();
        logger.info("---Winner---");
        logger.info("winners: " + winners);
    }
}
