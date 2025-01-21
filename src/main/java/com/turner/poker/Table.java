package com.turner.poker;

public class Table {

    private static int currentPlayerPosition;
    private static int currentBigBlindPosition = 1;

    private Table() {};

    public static void updateBlindPositions() {
        currentBigBlindPosition++;
        currentBigBlindPosition = (currentBigBlindPosition + 1) % Players.getPlayers().size();
        Players.assignBigBlindPosition(currentBigBlindPosition);
        Players.assignSmallBlindPosition(currentBigBlindPosition - 1);
    }

    public static void updateCurrentPlayerPosition() {
        currentPlayerPosition = (currentPlayerPosition + 1) % Players.getPlayers().size();
    }
}
