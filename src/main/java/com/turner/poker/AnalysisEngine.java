package com.turner.poker;

import java.util.List;

public class AnalysisEngine {

    public static HandRank royalFlush(List<Card> cards) {
        return HandRank.ROYAL_FLUSH;
    }

    public static HandRank straightFlush(List<Card> cards) {
        return HandRank.STRAIGHT_FLUSH;
    }

    public static HandRank fourOfAKind(List<Card> cards) {
        return HandRank.FOUR_OF_A_KIND;
    }

    public static HandRank flush(List<Card> cards) {
        return HandRank.FLUSH;
    }

    public static HandRank straight(List<Card> cards) {
        return HandRank.STRAIGHT;
    }

    public static HandRank threeOfAKind(List<Card> cards) {
        return HandRank.THREE_OF_A_KIND;
    }

    public static HandRank twoPair(List<Card> cards) {
        return HandRank.TWO_PAIR;
    }

    public static HandRank onePair(List<Card> cards) {
        return HandRank.ONE_PAIR;
    }

    public static HandRank highCard(List<Card> cards) {
        return HandRank.HIGH_CARD;
    }
}
