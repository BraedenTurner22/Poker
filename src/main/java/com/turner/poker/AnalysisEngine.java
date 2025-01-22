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
        Collections.sort(allCards, (card1, card2) -> Integer.compare(card1.getRank().getValue(),
                card2.getRank().getValue()));
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
        List<Winner> winners = new ArrayList<Winner>();

        for (PlayerResult playerResult : playerResults) {
            System.out.println("pre.....");
            System.out.println("pre.....bestHandPlayerId: " + bestHandPlayerId);
            System.out.println("pre.....bestHandRank: " + bestHandRank);
            System.out.println("pre.....bestCards: " + bestCards);
            System.out.println("pre.....winners: " + winners);

            System.out.println("pre.....playerResult.getId(): " + playerResult.getId());
            System.out.println("pre.....playerResult.getHandRank(): " + playerResult.getHandRank());
            System.out
                    .println("pre.....playerResult.getBestCards(): " + playerResult.getBestCards());
            System.out.println("pre.....winners: " + winners);

            if (playerResult.getHandRank().getValue() > bestHandRank.getValue()) {
                winners.clear();
                bestHandPlayerId = playerResult.getId();
                bestHandRank = playerResult.getHandRank();
                bestCards = playerResult.getBestCards();
                Winner winner = new Winner(bestHandPlayerId, bestHandRank, bestCards);
                winners.add(winner);
            } else if (playerResult.getHandRank().getValue() == bestHandRank.getValue()) {
                bestHandPlayerId = playerResult.getId();
                bestHandRank = playerResult.getHandRank();
                bestCards = playerResult.getBestCards();
                Winner winner = new Winner(playerResult.getId(), playerResult.getHandRank(),
                        playerResult.getBestCards());
                winners.add(winner);
            }

            System.out.println("post.....");
            System.out.println("post.....bestHandPlayerId: " + bestHandPlayerId);
            System.out.println("post.....bestHandRank: " + bestHandRank);
            System.out.println("post.....bestCards: " + bestCards);
            System.out.println("post.....winners: " + winners);

            System.out.println("post.....playerResult.getId(): " + playerResult.getId());
            System.out
                    .println("post.....playerResult.getHandRank(): " + playerResult.getHandRank());
            System.out.println(
                    "post.....playerResult.getBestCards(): " + playerResult.getBestCards());
            System.out.println("post.....winners: " + winners);
        }
        System.out.println("final.....winners: " + winners);
        return winners;
    }
}
