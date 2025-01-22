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

    public static PlayerResult checkForHighCard(Player player) {
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

    public static List<PlayerResult> determineBestHandForEachPlayer() {
        List<PlayerResult> playerResults = new ArrayList<>();
        for (Player player : Players.getPlayers()) {
            PlayerResult playerResult = AnalysisEngine.checkForRoyalFlush(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForStraightFlush(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForFourOfAKind(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForFlush(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForStraight(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForThreeOfAKind(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForTwoPair(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForOnePair(player);
            if (playerResult.getHandRank() == HandRank.NOTHING)
                playerResult = AnalysisEngine.checkForHighCard(player);
            playerResults.add(playerResult);
        }
        return playerResults;
    }

    public static List<Winner> determineWinners(List<PlayerResult> playerResults) {
        String bestHandPlayerId = null;
        HandRank bestHandRank = HandRank.NOTHING;
        List<Card> bestCards = null;
        for (PlayerResult playerResult : playerResults) {
            if (playerResult.getHandRank().getValue() > bestHandRank.getValue()) {
                bestHandPlayerId = playerResult.getId();
                bestHandRank = playerResult.getHandRank();
                bestCards = playerResult.getBestCards();
            }
        }
        Winner winner = new Winner(bestHandPlayerId, bestHandRank, bestCards);
        List<Winner> winners = new ArrayList<Winner>();
        winners.add(winner);
        return winners;
    }
}
