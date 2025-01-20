package com.turner.poker;

public class Poker {

    public static void main(String[] args) {
        Player playerOne = new Player(0, 0, 100);
        Player playerTwo = new Player(1, 1, 100);

        Game game = new Game();

        game.addPlayer(playerOne);
        game.addPlayer(playerTwo);



    }
}
