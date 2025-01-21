package com.turner.poker;

public class Pot {
    private static int value;

    private Pot() {};

    public static int value() {
        return value;
    }

    public static void add(int amount) {
        value += amount;
    }

    public static void clear() {
        value = 0;
    }
}
