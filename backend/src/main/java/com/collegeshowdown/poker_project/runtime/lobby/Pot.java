package com.collegeshowdown.poker_project.runtime.lobby;

public class Pot {
    public int amount;

    public Pot() {
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int newAmount) {
        amount = amount + newAmount;
    }
}
