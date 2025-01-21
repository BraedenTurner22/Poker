package com.turner.poker;

import java.util.ArrayList;
import java.util.HashMap;
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

    public static Winner determineWinner() {
        // handRank playerHandRank = null;
        // List<Card> allCards = new ArrayList<>();
        // allCards.addAll(Board.getCards());
        // allCards.addAll(player.getCards());
        // boolean containsRoyalFlush = false;
        // // Figure out how to pass in royal flush hands into containsAll() method
        // // as I iterate through the suits
        // for (card.suit suit : card.suit.values()) {
        // ArrayList<card> royalFlush = new ArrayList<>();
        // royalFlush.add(new card(suit, rank.TEN));
        // royalFlush.add(new card(suit, rank.JACK));
        // royalFlush.add(new card(suit, rank.QUEEN));
        // royalFlush.add(new card(suit, rank.KING));
        // royalFlush.add(new card(suit, rank.ACE));
        // if (allCards.containsAll(royalFlush)) {
        // playerHandRank = handRank.RFLUSH;
        // }
        // }
        // for (card.suit suit: card.suit.values()) {

        // }

        Map<String, HandRank> standings = new HashMap<>();

        for (Player player : Players.getPlayers()) {
            List<Card> allCards = new ArrayList<>();
            allCards.addAll(Board.getCards());
            allCards.addAll(player.getCards());

            HandRank handRank = HandRank.NOTHING;
            handRank = AnalysisEngine.royalFlush(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.straightFlush(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.fourOfAKind(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.flush(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.straight(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.threeOfAKind(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.twoPair(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.onePair(allCards);
            if (handRank == HandRank.NOTHING)
                handRank = AnalysisEngine.highCard(allCards);
            if (handRank != HandRank.NOTHING) {
                standings.put(player.getId(), handRank);
            }
        }

        HandRank bestHandRank = HandRank.NOTHING;
        String bestHandId = null;
        for (Map.Entry<String, HandRank> entry : standings.entrySet()) {
            if (entry.getValue().getValue() > bestHandRank.getValue()) {
                bestHandId = entry.getKey();
                bestHandRank = entry.getValue();
            }
        }

        Winner winner = new Winner(bestHandId, bestHandRank);
        return winner;
    }
}
