package com.turner.poker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Game {

    private Game() {}

    // public static void addPlayer(Player player) {
    // Players.addPlayer(player);
    // };

    // public static void removePlayer(Player player) {
    // Players.removePlayer(player);
    // };

    public static void start() {};

    public static void end() {}

    public static void dealCardToPlayer(Player player) {
        player.acceptCard(Deck.getTopCard());
    }

    public static void burn() {
        Board.burn();
    }

    public static void flop() {
        dealCardsToBoard(3);
    }

    public static void turn() {
        dealCardsToBoard(1);
    }

    public static void river() {
        dealCardsToBoard(1);
    }

    private static void dealCardsToBoard(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++)
            cards.add(Deck.getTopCard());
        Board.dealCards(cards);
    }

    // public static void playerFolds(Player player) {
    // discardedCards.addAll(player.removeCards());
    // incrementPlayer();
    // }

    // public static void playerChecks(Player player) {
    // incrementPlayer();
    // }

    // public static void playerRaises(Player player, int raise) {
    // pot += raise;
    // incrementPlayer();
    // }

    // private static void incrementPlayer() {
    // currentPlayer = (currentPlayer + 1) % Players.getPlayers().size();
    // }


    // public static Winner determineWinner() {
    // Map<String, Hand> standings = new HashMap<>();

    // Hand hand;
    // for (Player player : Players.getPlayers()) {
    // List<Card> allCards = new ArrayList<>();
    // allCards.addAll(player.getCards());
    // allCards.addAll(Board.getCards());

    // // HandRank handRank = HandRank.NOTHING;
    // // handRank = AnalysisEngine.royalFlush(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.straightFlush(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.fourOfAKind(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.flush(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.straight(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.threeOfAKind(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.twoPair(allCards);
    // // if (handRank == HandRank.NOTHING)
    // // handRank = AnalysisEngine.onePair(allCards);
    // hand = AnalysisEngine.highCard(allCards);
    // // if (hand.getHandRank() == HandRank.NOTHING)
    // // hand = AnalysisEngine.highCard(allCards);

    // standings.put(player.getId(), hand);
    // }

    // for (Map.Entry<String, Hand> entry : standings.entrySet())
    // System.out.println("\n....." + entry);

    // // String winnerId = null;
    // // List<Card> winningCards = new ArrayList<>();
    // // HandRank winnerHandRank = HandRank.NOTHING;

    // // for (Map.Entry<String, Hand> entry : standings.entrySet()) {
    // // System.out.println("winnerId: " + entry.getKey());
    // // System.out.println("bestHandRank: " + entry.getValue().getHandRank());
    // // System.out.println("bestCards: " + entry.getValue().getBestCards());
    // // if (entry.getValue().getHandRank().getValue() >= winnerHandRank.getValue()) {
    // // for (Card card : entry.getValue().getBestCards()) {
    // // if (card.getRank().getValue() >= )
    // // }
    // // winnerId = entry.getKey();
    // // winnerHandRank = entry.getValue().getHandRank();
    // // winningCards = entry.getValue().getBestCards();
    // // }
    // // }

    // // return new Winner(winnerId, winnerHandRank, winningCards);
    // }

    public static Map<String, Hand> determineBestHandForEachPlayer() {
        return AnalysisEngine.determineBestHandForEachPlayer();
    }

    public static Winner determineWinner(Map<String, Hand> bestHands) {
        HandRank bestHandRank = HandRank.NOTHING;
        String bestHandId = null;
        List<Card> bestHand = null;
        for (Map.Entry<String, Hand> entry : bestHands.entrySet()) {
            if (entry.getValue().getHandRank().getValue() > bestHandRank.getValue()) {
                bestHandId = entry.getKey();
                bestHandRank = entry.getValue().getHandRank();
                bestHand = entry.getValue().getBestCards();
            }
        }

        return new Winner(bestHandId, bestHandRank, bestHand);
    }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        return builder.toString();
    }
}
