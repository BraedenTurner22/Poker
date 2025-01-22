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
        // System.out.println("---FLOP-------------------------------------");
        Game.flop();
        // System.out.println(Board.staticToString());

        Game.burn();
        // System.out.println("---TURN-------------------------------------");
        Game.turn();
        // System.out.println(Board.staticToString());

        Game.burn();
        // System.out.println("---RIVER-------------------------------------");
        Game.river();
        // System.out.println(Board.staticToString());

        System.out.println("---Board-------------------------------------");
        System.out.println(Board.staticToString());

        System.out.println("---Players-------------------------------------");
        System.out.println(Players.staticToString());

        List<PlayerResult> playerResults = Game.determineBestHandForEachPlayer();

        System.out.println("---Player Result-------------------------------------");
        for (PlayerResult playerResult : playerResults)
            System.out.println(playerResult);

        List<Winner> winners = Game.determineWinners(playerResults);

        System.out.println("---Winners-------------------------------------");
        for (Winner winner : winners)
            System.out.println(winner);

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
        //Test line
    }
}
