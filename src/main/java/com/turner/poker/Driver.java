package com.turner.poker;

public class Driver {

    public static void main(String[] args) {
        Player playerOne = new Player(0, 0, 100);
        Player playerTwo = new Player(1, 1, 100);

        Game game = new Game();

        game.addPlayer(playerOne);
        game.addPlayer(playerTwo);

        game.start();
        game.burnCard();
        game.dealCardToPlayer(playerOne);
        game.dealCardToPlayer(playerTwo);
        game.dealCardToPlayer(playerOne);
        game.dealCardToPlayer(playerTwo);
        game.burnCard();
        game.flop();
        game.burnCard();
        game.turn();
        game.burnCard();
        game.river();
        game.determineWinner();
    }
}
