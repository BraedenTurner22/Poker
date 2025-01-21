package com.turner.poker;

public class Driver {

    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            Player player = new Player(Integer.toString(i), i, 100);
            Players.addPlayer(player);
        }

        // Game.start();
        Deck.shuffle();
        Deck.dealCardsToPlayers(2);

        Players.printPlayerCards();

        Game.burn();
        System.out.println("flop -------------------------------------");
        Game.flop();
        Board.printBoard();

        Game.burn();
        System.out.println("turn -------------------------------------");
        Game.turn();
        Board.printBoard();

        Game.burn();
        System.out.println("river -------------------------------------");
        Game.river();
        Board.printBoard();

        System.out.println("Deck size: " + Deck.getDeckSize());

        Winner winner = Game.determineWinner();

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
