package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalysisEngine {

    public static PlayerResult checkForRoyalFlush(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForStraightFlush(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForFourOfAKind(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForFlush(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForStraight(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForThreeOfAKind(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForTwoPair(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    public static PlayerResult checkForOnePair(Player player) {
        return new PlayerResult(player.getId(), HandRank.NOTHING);
    }

    // public static PlayerResult checkForHighCard(List<Card> cards) {
    // Collections.sort(cards,
    // (c1, c2) -> Integer.compare(c1.getRank().getValue(), c2.getRank().getValue()));
    // Card highCard = cards.get(cards.size() - 1);
    // List<Card> bestCards = new ArrayList<>();
    // bestCards.add(highCard);
    // return new Hand(cards, HandRank.HIGH_CARD, bestCards);
    // }

    public static PlayerResult xcheckForHighCard(Player player) {
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(player.getCards());
        allCards.addAll(Board.getCards());
        Collections.sort(allCards,
                (c1, c2) -> Integer.compare(c1.getRank().getValue(), c2.getRank().getValue()));
        Card highCard = allCards.get(allCards.size() - 1);
        List<Card> bestCards = new ArrayList<>();
        bestCards.add(highCard);
        return new PlayerResult(player.getId(), HandRank.HIGH_CARD, allCards, bestCards);
    }

    // public static Map<String, Hand> determineBestHandForEachPlayer() {
    // Map<String, Hand> results = new HashMap<>();
    // for (Player player : Players.getPlayers()) {
    // List<Card> allCards = new ArrayList<>();
    // allCards.addAll(player.getCards());
    // allCards.addAll(Board.getCards());
    // Hand hand = AnalysisEngine.checkForRoyalFlush(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForStraightFlush(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForFourOfAKind(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForFlush(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForStraight(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForThreeOfAKind(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForTwoPair(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForOnePair(allCards);
    // if (hand.getHandRank() == HandRank.NOTHING)
    // hand = AnalysisEngine.checkForHighCard(allCards);

    // results.put(player.getId(), hand);
    // }

    // return results;
    // }

    public static List<PlayerResult> xdetermineBestHandForEachPlayer() {
        List<PlayerResult> playerResults = new ArrayList<>();
        // Map<String, Hand> results = new HashMap<>();
        for (Player player : Players.getPlayers()) {
            PlayerResult playerResult = AnalysisEngine.checkForRoyalFlush(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForStraightFlush(player);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForFourOfAKind(allCards);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForFlush(allCards);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForStraight(allCards);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForThreeOfAKind(allCards);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForTwoPair(allCards);
            // if (hand.getHandRank() == HandRank.NOTHING)
            // hand = AnalysisEngine.checkForOnePair(allCards);
            // if (playerResult.getHandRank() == HandRank.NOTHING)
            playerResult = AnalysisEngine.xcheckForHighCard(player);

            playerResults.add(playerResult);
        }

        return playerResults;
    }

    // public static List<Winner> determineWinners(Map<String, Hand> bestHandForEachPlayer) {
    // String bestHandId = null;
    // HandRank bestHandRank = HandRank.NOTHING;
    // List<Card> bestHandCards = null;
    // List<Hand> bestHands = null;
    // // Map<HandRank, List<String>> handRankToIdMap = new HashMap<>();

    // // for (HandRank handRank : HandRank.values()) {
    // // handRankToIdMap.put(handRank, new ArrayList<String>());
    // // }
    // for (Map.Entry<String, Hand> entry : bestHandForEachPlayer.entrySet()) {
    // System.out.println(">>>>>>>>>>>>>>> key: " + entry.getKey());
    // System.out.println(">>>>>>>>>>>>>>> value: " + entry.getValue());

    // // handRankToIdMap.get(entry.getValue().getHandRank()).add(entry.getKey());

    // if (entry.getValue().getHandRank().getValue() > bestHandRank.getValue()) {
    // Hand hand = new Hand(entry.getValue().getBestCards(), entry.getValue().getHandRank(),
    // entry.getValue().getBestCards());
    // // bestHandId = entry.getKey();
    // // bestHandRank = entry.getValue().getHandRank();
    // // bestHandCards = entry.getValue().getBestCards();
    // } else if (entry.getValue().getHandRank().getValue() == bestHandRank.getValue()) {
    // }


    // // for (int i = handRankToIdMap.size(); i > 0; i--) {
    // // if handRankToIdMap.get()
    // // }
    // List<Winner> winners = new ArrayList<>();
    // winners.add(new Winner(bestHandId, bestHandRank, bestHand));
    // return winners;
    // }
}
