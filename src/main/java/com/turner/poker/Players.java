package com.turner.poker;

import java.util.HashMap;
import java.util.Map;

public class Players {

    private static Map<String, Player> players = new HashMap<>();

    private Players() {}

    public static void reset() {
        players.clear();
    }

    public static void addPlayer(Player player) {
        players.put(player.getId(), player);
    }

    // public static void removePlayer(Player player) {
    // players.remove(player);
    // }

    public static Map<String, Player> getPlayers() {
        // return Collections.unmodifiableMap(players);
        return players;
    }

    public static Player getPlayer(String id) {
        return players.get(id);
    }

    // public static void assignBigBlindPosition(int position) {
    // players.get(position).setBigBlindPosition(true);
    // }

    // public static void assignSmallBlindPosition(int position) {
    // players.get(position).setSmallBlindPosition(true);
    // }

    public static String staticToString() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            builder.append(entry.getValue().toString() + "\n\n");
        }
        return builder.toString();
    }

    // public static List<PlayerResult> getPlayerResults() {
    // List<PlayerResult> playerResults = new ArrayList<>();
    // for (Player player : players) {
    // playerResults.add(player.getPlayerResults());
    // }
    // return playerResults;
    // }
}
