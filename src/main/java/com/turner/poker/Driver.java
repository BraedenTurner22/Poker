package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

public class Driver {

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            Players.addPlayer(new Player(Integer.toString(i), i, 100));
        }

        // Game.start();
        Deck.shuffle();
        // Deck.dealCardsToPlayers(2);
        List<Card> testCardsToPlayers = new ArrayList<>();
        testCardsToPlayers.add(new Card(Rank.ACE, Suit.DIAMONDS));
        testCardsToPlayers.add(new Card(Rank.TEN, Suit.HEARTS));
        testCardsToPlayers.add(new Card(Rank.FIVE, Suit.CLUBS));
        testCardsToPlayers.add(new Card(Rank.TEN, Suit.SPADES));
        Deck.dealTestCardsToPlayers(testCardsToPlayers);

        // Game.burn();
        // // System.out.println("---FLOP-------------------------------------");
        // Game.flop();
        // // System.out.println(Board.staticToString());

        // Game.burn();
        // // System.out.println("---TURN-------------------------------------");
        // Game.turn();
        // // System.out.println(Board.staticToString());

        // Game.burn();
        // // System.out.println("---RIVER-------------------------------------");
        // Game.river();
        // // System.out.println(Board.staticToString());

        List<Card> testCardsToBoard = new ArrayList<>();
        testCardsToBoard.add(new Card(Rank.TWO, Suit.DIAMONDS));
        testCardsToBoard.add(new Card(Rank.FOUR, Suit.DIAMONDS));
        testCardsToBoard.add(new Card(Rank.SIX, Suit.DIAMONDS));
        testCardsToBoard.add(new Card(Rank.EIGHT, Suit.DIAMONDS));
        testCardsToBoard.add(new Card(Rank.TEN, Suit.DIAMONDS));
        Game.layoutTestCards(testCardsToBoard);

        System.out.println("---Board-------------------------------------");
        System.out.println(Board.staticToString());

        System.out.println("---Players-------------------------------------");
        System.out.println(Players.staticToString());

        System.out
                .println("---Best Hand Rank For Each Player-------------------------------------");
        Game.determineBestHandRankForEachPlayer();
        for (Player player : Players.getPlayers()) {
            // for (Map.Entry<String, PlayerResult> entry : Players.getPlayerResults().entrySet()) {
            // for (PlayerResult playerResult : PlayerResults.getPlayerResults()) {
            // System.out.println(entry.getKey() + " : " + entry.getValue().getBestCards());
            // System.out.println(entry.getValue());
            System.out.println(player);
        }

        System.out.println(
                "\n---Best Hand Rank Amongst All Players-------------------------------------");
        List<PlayerResult> bestHandAmongstAllPlayers = Game.determineBestHandAmongstAllPlayers();
        for (PlayerResult playerResult : bestHandAmongstAllPlayers) {
            // System.out.println(playerResult.getId() + " : " + playerResult.getBestCards());
            System.out.println(playerResult + "\n");
        }

        // System.out.println("---Winners-------------------------------------");
        // for (Winner winner : winners)
        // System.out.println(winner);

        // System.out.println("---Winners-------------------------------------");
        // for (Winner winner : winners) {
        // System.out.println(winner);
        // }

        // Table.assignBlinds();

        // Players.printPlayerCards();

        // Game.burnCard();

        // players.play()

        // Game.burnCard();
        // Game.flop();

        // Game.burnCard();
        // Game.turn();
        // players.play()

        // Game.burnCard();
        // Game.river();
        // players.play()

        // Game.determineWinner();
    }
}
