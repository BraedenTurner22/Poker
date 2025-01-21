package com.turner.poker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalysisEngine {

    public static Hand royalFlush(List<Card> cards) {
        // return HandRank.ROYAL_FLUSH;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand straightFlush(List<Card> cards) {
        // return HandRank.STRAIGHT_FLUSH;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand fourOfAKind(List<Card> cards) {
        // return HandRank.FOUR_OF_A_KIND;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand flush(List<Card> cards) {
        // return HandRank.FLUSH;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand straight(List<Card> cards) {
        // return HandRank.STRAIGHT;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand threeOfAKind(List<Card> cards) {
        // return HandRank.THREE_OF_A_KIND;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand twoPair(List<Card> cards) {
        // return HandRank.TWO_PAIR;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand onePair(List<Card> cards) {
        // return HandRank.ONE_PAIR;
        return new Hand(cards, HandRank.NOTHING, cards);
    }

    public static Hand highCard(List<Card> cards) {
        Rank bestRank = Rank.TWO;
        Card bestCard = null;
        for (Card card : cards) {
            if (card.getRank().getValue() > bestRank.getValue()) {
                bestRank = card.getRank();
                bestCard = card;
            }
        }
        List<Card> bestCards = new ArrayList<>();
        bestCards.add(bestCard);
        return new Hand(cards, HandRank.HIGH_CARD, bestCards);
    }

    public static Map<String, Hand> determineBestHandForEachPlayer() {
        // return determineBestHighCardForEachPlayer();

        Map<String, Hand> results = new HashMap<>();
        for (Player player : Players.getPlayers()) {
            List<Card> allCards = new ArrayList<>();
            allCards.addAll(player.getCards());
            allCards.addAll(Board.getCards());
            Hand hand = AnalysisEngine.royalFlush(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.straightFlush(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.fourOfAKind(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.flush(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.straight(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.threeOfAKind(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.twoPair(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.onePair(allCards);
            if (hand.getHandRank() == HandRank.NOTHING)
                hand = AnalysisEngine.highCard(allCards);

            results.put(player.getId(), hand);
        }

        return results;
    }

    public static Map<String, Hand> determineBestHighCardForEachPlayer() {
        // HandRank handRank = HandRank.NOTHING;
        // handRank = AnalysisEngine.royalFlush(allCards);

        Map<String, Hand> results = new HashMap<>();
        for (Player player : Players.getPlayers()) {
            List<Card> allCards = new ArrayList<>();
            allCards.addAll(player.getCards());
            allCards.addAll(Board.getCards());

            HandRank handRank = HandRank.HIGH_CARD;
            Rank highCardRank = Rank.TWO;
            Card bestCard = null;
            List<Card> bestCards = new ArrayList<>();
            for (Card card : allCards) {
                if (card.getRank().getValue() > highCardRank.getValue()) {
                    highCardRank = card.getRank();
                    bestCard = card;
                }
            }

            bestCards.add(bestCard);
            Hand hand = new Hand(allCards, handRank, bestCards);
            results.put(player.getId(), hand);
        }
        return results;
    }

}
