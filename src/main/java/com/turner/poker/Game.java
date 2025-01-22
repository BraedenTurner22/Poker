package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

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
        layoutCards(3);
    }

    public static void turn() {
        layoutCards(1);
    }

    public static void river() {
        layoutCards(1);
    }

    private static void layoutCards(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++)
            cards.add(Deck.getTopCard());
        Board.layoutCards(cards);
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

    // public static Map<String, Hand> determineBestHandForEachPlayer() {
    // return AnalysisEngine.determineBestHandForEachPlayer();
    // }

    public static List<PlayerResult> xdetermineBestHandForEachPlayer() {
        return AnalysisEngine.xdetermineBestHandForEachPlayer();
    }

    // public static List<Winner> determineWinners(Map<String, Hand> bestHandForEachPlayer) {
    // return AnalysisEngine.determineWinners(bestHandForEachPlayer);
    // }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        return builder.toString();
    }
}
