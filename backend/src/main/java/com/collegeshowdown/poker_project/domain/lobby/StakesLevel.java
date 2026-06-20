package com.collegeshowdown.poker_project.domain.lobby;

public enum StakesLevel {
    LOW(1, 2, 40),
    MEDIUM(2, 5, 100),
    HIGH(5, 10, 200);

    public final int smallBlind;
    public final int bigBlind;
    public final int minimumBuyIn;

    StakesLevel(int smallBlind, int bigBlind, int minimumBuyIn) {
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.minimumBuyIn = minimumBuyIn;
    }
}
