package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private Game() {}

    public static void addPlayer(Player player) {
        Players.addPlayer(player);
    };

    public static void removePlayer(Player player) {
        Players.removePlayer(player);
    };

    public static void start() {
        for (Player player : Players.getPlayers())
            dealCardToPlayer(player);
    };

    public static void end() {}

    public static void dealCardToPlayer(Player player) {
        player.acceptCard(Deck.getTopCard());
    }

    public static void flop() {
        dealCards(3);
    }

    public static void turn() {
        dealCards(1);
    }

    public static void river() {
        dealCards(1);
    }

    private static void dealCards(int count) {
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

    public static void determineWinner() {}

    // public static int getPot() {
    // return pot;
    // }

    // public static int getCurrentPlayer() {
    // return currentPlayer;
    // }
}
