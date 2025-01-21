package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Players {
    // private static Players instance;
    private static List<Player> players = new ArrayList<>();

    private Players() {}

    // public static Players getInstance() {
    // if (instance != null) {
    // return instance;
    // } else {
    // instance = new Players();
    // return instance;
    // }
    // }

    public static void addPlayer(Player player) {
        players.add(player);
    }

    public static void removePlayer(Player player) {
        players.remove(player);
    }

    public static List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public static void assignBlindPositions(int bigBlindPosition) {
        players.get(bigBlindPosition - 1).setBigBlindPosition(false);
        players.get(bigBlindPosition - 1).setSmallBlindPosition(true);
        players.get(bigBlindPosition).setBigBlindPosition(true);
    }

    public static void printPlayerCards() {
        System.out.println(".......................in printPlayerCards()");
        for (Player player : players) {
            System.out.println(player.toString());
        }
    }
}
