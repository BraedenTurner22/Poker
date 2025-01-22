package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Players {

    private static List<Player> players = new ArrayList<>();

    private Players() {}

    public static void reset() {
        players.clear();
    }

    public static void addPlayer(Player player) {
        players.add(player);
    }

    // public static void removePlayer(Player player) {
    // players.remove(player);
    // }

    public static List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    // public static void assignBigBlindPosition(int position) {
    // players.get(position).setBigBlindPosition(true);
    // }

    // public static void assignSmallBlindPosition(int position) {
    // players.get(position).setSmallBlindPosition(true);
    // }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Player player : players)
            builder.append(player.toString() + "\n");
        return builder.toString();
    }
}
