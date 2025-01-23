package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalysisEngine {

    public static PlayerResult checkForRoyalFlush(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForStraightFlush(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForFourOfAKind(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForFlush(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForStraight(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForThreeOfAKind(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForTwoPair(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
    }

    public static PlayerResult checkForOnePair(Player player) {
        return new PlayerResult(player, HandRank.NOTHING, null);
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
        return new PlayerResult(player, HandRank.HIGH_CARD, bestCards);
    }

    public static void determineBestHandRankForEachPlayer() {
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
            // PlayerResults.updatePlayerResult(playerResult);
            player.setPlayerResult(playerResult);
        }
    }

    public static List<PlayerResult> determineBestHandRankAmongstAllPlayers() {
        HandRank bestHandRank = HandRank.NOTHING;
        List<PlayerResult> bestHands = new ArrayList<PlayerResult>();

        // for (Map.Entry<String, PlayerResult> entry : PlayerResults.getPlayerResults().entrySet())
        // {
        // if (entry.getValue().getHandRank().getValue() > bestHandRank.getValue()) {
        // bestHands.clear();
        // bestHandRank = entry.getValue().getHandRank();
        // bestHands.add(entry.getValue());
        // continue;
        // }
        // if (entry.getValue().getHandRank().getValue() == bestHandRank.getValue()) {
        // bestHands.add(entry.getValue());
        // continue;
        // }
        // }

        for (Player player : Players.getPlayers()) {
            if (player.getPlayerResults().getHandRank().getValue() > bestHandRank.getValue()) {
                bestHands.clear();
                bestHandRank = player.getPlayerResults().getHandRank();
                bestHands.add(player.getPlayerResults());
                continue;
            }
            if (player.getPlayerResults().getHandRank().getValue() == bestHandRank.getValue()) {
                bestHands.add(player.getPlayerResults());
                continue;
            }
        }
        return bestHands;
    }
}
// for (Map.Entry<String, PlayerResult> entry : PlayerResults.getPlayerResults().entrySet()) {
// System.out.println("pre.....");
// System.out.println("pre.....bestHandPlayerId: " + bestHandPlayerId);
// System.out.println("pre.....bestHandRank: " + bestHandRank);
// System.out.println("pre.....bestHandCards: " + bestHandCards);
// System.out.println("pre.....winners: " + winners);

// System.out.println("pre.....playerResult.getId(): " + playerResult.getId());
// System.out.println("pre.....playerResult.getHandRank(): " + playerResult.getHandRank());
// System.out
// .println("pre.....playerResult.getBestCards(): " + playerResult.getBestCards());
// System.out.println("pre.....winners: " + winners);

// if (playerResult.getHandRank().getValue() > bestHandRank.getValue()) {
// System.out.println(
// "pre.....playerResult.getHandRank().getValue() > bestHandRank.getValue()");
// winners.clear();
// bestHandPlayerId = playerResult.getId();
// bestHandRank = playerResult.getHandRank();
// bestHandCards = playerResult.getBestCards();
// Winner winner = new Winner(bestHandPlayerId, bestHandRank, bestCards);
// winners.add(winner);
// } else if (playerResult.getHandRank().getValue() == bestHandRank.getValue()) {
// System.out.println(
// "pre.....playerResult.getHandRank().getValue() == bestHandRank.getValue()");
// bestHandPlayerId = playerResult.getId();
// bestHandRank = playerResult.getHandRank();
// bestHandCards = playerResult.getBestCards();
// Winner winner = new Winner(playerResult.getId(), playerResult.getHandRank(),
//



// System.out.println("post.....");
// System.out.println("post.....bestHandPlayerId: " + bestHandPlay
// System.out.println("post.....bestHandRank: " + best



// ut.println("post.....playerResult.getId(): " + playerResult.getId());
// System.out



// List<Card> bestCards = null;
// <Winner> winners = new ArrayList<Winner>();

// t playerResult : playerResults) {



// System.out.println("pre.....bestCards: " + bestCards);
// System.out.println("pre.....winners: " + winners);



// .println("pre.....playerResult.getBestCards(): " + playerResult.getBestCards());
// em.out.println("pre.....winners: " + winners);

// playerResult.getHandRank().getValue() > bestHandRank.getValue()) {
// ers.clear();
// bestHandPlayerId = playerResult.getId();
// bestHandRank = playerResult.getHandRank();
// bestCards = playerResult.getBestCards();
// Winner winner = new Winner(bestHandPlayerId, bestHandRank, bestCards);
// winners.add(winner);
// se if (playerResult.getHandRank().getValue() == bestHandRank.getValue()) {
// bestHandPlayerId = playerResult.getId();
//



// winners.add(winner);
// }



// System.out.println("post.....bestCards: " + bestCards);
//

// System.out.println("post.....playerResult.getId(): " + playerResult.getId());
//
// .println("post.....playerResult.getHandRank(): " + playerResult.getHandRank());
// System.out.println(
// "post.....playerResult.getBestCards(): " + playerResult.getBestCards());
// System.out.println("post.....winners: " + winners);
// }
// System.out.println("final.....winners: " + winners);
// return winners;
// }
// }
