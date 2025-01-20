package com.turner.poker;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private Deck deck;
    private List<Player> players;
    private List<Card> cardsOnTable;
    private List<Card> discardedCards;
    private int pot;
    private int currentPlayer;

    public Game() {
        deck = new Deck();
        players = new ArrayList<>();
        discardedCards = new ArrayList<>();
        currentPlayer = 0;
    }

    public void addPlayer(Player player) {
        players.add(player);
    };

    public void removePlayer(Player player) {
        players.remove(player);
    };

    public void startGame() {
        for (Player player : players)
            dealCardToPlayer(player);
    };

    public void endGame() {}

    public void burnCard() {
        discardedCards.add(deck.getTopCard());
    }

    public void dealCardToPlayer(Player player) {
        player.receiveCard(deck.getTopCard());
    }

    public void flop() {
        for (int i = 0; i < 3; i++)
            cardsOnTable.add(deck.getTopCard());
    }

    public void turn() {
        cardsOnTable.add(deck.getTopCard());
    }

    public void river() {
        cardsOnTable.add(deck.getTopCard());
    }

    public void playerFolds(Player player) {
        discardedCards.addAll(player.removeCards());
        incrementPlayer();
    }

    public void playerChecks(Player player) {
        incrementPlayer();
    }

    public void playerRaises(Player player, int raise) {
        pot += raise;
        incrementPlayer();
    }

    private void incrementPlayer() {
        currentPlayer = (currentPlayer + 1) % players.size();
    }

    public void determineWinner(List<Player> players) {}

    public int getPot() {
        return pot;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }



}
