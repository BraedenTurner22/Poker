package com.collegeshowdown.poker_project.model;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;

import java.util.List;

public class Lobby {
    @Id
    private int id;
    private Deck deck;
    private List<Card> board;
    private List<Player> players;
    // private AnalysisEngine analysisEngine;
    private static Lobby instance;

    public Lobby() {
        instance = this;
    }

    public static Lobby getInstance() {
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
        return AnalysisEngine.getWinners(players);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("Game...");
        stringBuilder.append("\n...board: " + board);
        stringBuilder.append("\n...players: " + players);
        return stringBuilder.toString();
    }
}
