package com.turner.poker;

import java.util.List;

public class Game {
    private Deck deck;
    private List<Card> board;
    private List<Player> players;
    private AnalysisEngine analysisEngine = new AnalysisEngine();
    private static Game instance;

    public Game() {
        instance = this;
    }

    public static Game getInstance() {
        return instance;
    }

    public Deck getDeck() {
        return deck;
    }

    public void setDeck(Deck deck) {
        this.deck = deck;
    }

    public List<Card> getBoard() {
        return board;
    }

    public void setBoard(List<Card> board) {
        this.board = board;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public void play() {}

    public List<Winner> getWinners() {
        return analysisEngine.getWinners(players);
    }

    @Override
    public String toString() {
        return "Game{board=" + board + ", players=" + players + "}";
    }
}
