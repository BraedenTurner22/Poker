package com.turner.poker;

import java.util.List;

public class Driver {

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            Players.addPlayer(new Player(Integer.toString(i), i, 100));
        }

        // Game.start();
        Deck.shuffle();
        Deck.dealCardsToPlayers(2);

        Game.burn();
        System.out.println("---FLOP-------------------------------------");
        Game.flop();
        System.out.println(Board.staticToString());

        Game.burn();
        System.out.println("---TURN-------------------------------------");
        Game.turn();
        System.out.println(Board.staticToString());

        Game.burn();
        System.out.println("---RIVER-------------------------------------");
        Game.river();
        System.out.println(Board.staticToString());

        System.out.println("---Players-------------------------------------");
        System.out.println(Players.staticToString());

        // Map<String, Hand> bestHandsForEachPlayer = Game.determineBestHandForEachPlayer();

        // System.out.println("---Best Hand For Each Player-------------------------------------");
        // System.out.println(Utils.bestHandsForEachPlayerToString(bestHandsForEachPlayer));


        // List<Winner> winner = Game.determineWinners(bestHandsForEachPlayer);

        // System.out.println("Winner: " + winner);

        // Map<String, Hand> bestHandForEachPlayer = Game.determineBestHandForEachPlayer();
        List<PlayerResult> playerResults = Game.xdetermineBestHandForEachPlayer();

        for (PlayerResult playerResult : playerResults)
            System.out.println(playerResult);

        // System.out.println("---Best Hand For Each Player-------------------------------------");
        // System.out.println(Utils.bestHandsForEachPlayerToString(bestHandForEachPlayer));

        // List<Winner> winners = Game.determineWinners(bestHandForEachPlayer);

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
