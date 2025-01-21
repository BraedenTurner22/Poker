package com.turner.poker;

import java.util.Map;

public class Driver {

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            Players.addPlayer(new Player(Integer.toString(i), i, 100));
        }

        // Game.start();
        Deck.shuffle();
        Deck.dealCardsToPlayers(2);

        System.out.println(Players.staticToString());

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

        // System.out.println("Deck size: " + Deck.getDeckSize());


        System.out.println("---Players-------------------------------------");

        for (Player player : Players.getPlayers()) {
            System.out.println(player.toString());
        }

        Map<String, Hand> bestHandsForEachPlayer = Game.determineBestHandForEachPlayer();

        // System.out.println("\nBest hands: " + bestHandsForEachPlayer);

        // System.out.println("Best Hands...");
        // for (Map.Entry<String, Hand> entry : bestHandsForEachPlayer.entrySet()) {
        // StringBuilder builder = new StringBuilder();
        // builder.append("Player\n");
        // builder.append(" ID: " + entry.getKey() + "\n");
        // builder.append(" Card(s):\n");
        // for (Card card : entry.getValue().getBestCards()) {
        // builder.append(" " + card);
        // }
        // // builder.append("Best Hand: " + entry.getValue());
        // System.out.println(builder.toString());
        // }

        System.out.println("---Best Cards-------------------------------------");

        for (Map.Entry<String, Hand> entry : bestHandsForEachPlayer.entrySet()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Player [");
            builder.append("id: " + entry.getKey());
            builder.append(", cards: ");
            int count = 0;
            for (Card card : entry.getValue().getBestCards()) {
                count++;
                builder.append(card.toString());
                if (count < entry.getValue().getBestCards().size())
                    builder.append(", ");
            }
            builder.append("]");
            System.out.println(builder.toString());
        }

        // Winner winner = Game.determineWinner(bestHands);

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
