package com.turner.poker;

public class Table {

    private static int bigBlindPosition = 1;

    private Table() {};

    public static void assignBlinds() {
        bigBlindPosition++;
        bigBlindPosition = (bigBlindPosition + 1) % Players.getPlayers().size();
        Players.assignBlindPositions(bigBlindPosition);

    }

}
