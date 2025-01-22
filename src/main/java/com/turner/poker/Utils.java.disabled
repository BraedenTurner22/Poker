package com.turner.poker;

import java.util.Map;

public class Utils {

    // public static String playersToString() {
    // }

    public static String bestHandsForEachPlayerToString(Map<String, Hand> bestHandsForEachPlayer) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Hand> entry : bestHandsForEachPlayer.entrySet()) {
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
            builder.append("]\n");
        }
        return builder.toString();
    }
}
