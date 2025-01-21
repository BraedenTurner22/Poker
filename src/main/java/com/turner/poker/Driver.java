package com.turner.poker;

public class Driver {

    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            Player player = new Player(i, i, 100);
            Players.addPlayer(player);
        }
        // Deck.printDeck();
        // System.out.println("-----------------------------------------------");
        Deck.shuffle();
        // Deck.printDeck();

        Deck.dealCardsToPlayers(2);

        Players.printPlayerCards();

        Game.flop();
        Board.printBoard();
        System.out.println("-------------------------------------");

        Game.turn();
        Board.printBoard();
        System.out.println("-------------------------------------");

        Game.river();
        Board.printBoard();
        System.out.println("-------------------------------------");

        // Table.assignBlinds();


        // Game.start();

        // Players.printPlayerCards();

        // Game.burnCard();
        // Game.dealCardToPlayer(playerOne);
        // Game.dealCardToPlayer(playerTwo);
        // Game.dealCardToPlayer(playerOne);
        // Game.dealCardToPlayer(playerTwo);

        // playerOne.play();
        // playerTwo.play();

        // Game.burnCard();
        // Game.flop();
        // playerOne.play();
        // playerTwo.play();

        // Game.burnCard();
        // Game.turn();
        // playerOne.play();
        // playerTwo.play();

        // Game.burnCard();
        // Game.river();
        // playerOne.play();
        // playerTwo.play();

        // Game.determineWinner();
    }
}
