package com.collegeshowdown.poker_project.domain.lobby;

import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Pot {

    public List<ConnectedPlayer> playersInPot;
    public int currentAmountInPot;

    /**
     * True when this pot holds only its single contributor's own unmatched
     * chips (an uncalled bet) — it goes straight back to them, which is a
     * refund, not a win.
     */
    public boolean uncalledBet;

    /** Working pot: all active players eligible, empty to start. */
    public Pot(List<ConnectedPlayer> allPlayers) {
        this.playersInPot = new ArrayList<>(allPlayers);
        this.currentAmountInPot = 0;
    }

    /** Finalized side/main pot produced by recalculatePots. */
    public Pot(int amount, List<ConnectedPlayer> eligiblePlayers) {
        this.currentAmountInPot = amount;
        this.playersInPot = new ArrayList<>(eligiblePlayers);
    }

    public int getAmountInPot() {
        return currentAmountInPot;
    }

    public void addToAmountToPot(int newAmount) {
        currentAmountInPot += newAmount;
    }

    public void emptyPot() {
        currentAmountInPot = 0;
    }

    public boolean isUncalledBet() {
        return uncalledBet;
    }

    public List<ConnectedPlayer> getPlayersInPot() {
        return playersInPot;
    }

    /**
     * Players who can actually win this pot: contributed and have not folded.
     * A player removed via foldCards() is already gone from playersInPot,
     * so this filter is a safety net.
     */
    public List<ConnectedPlayer> getEligiblePlayers() {
        if (playersInPot == null) return new ArrayList<>();
        return playersInPot.stream()
                .filter(p -> p.getPlayerState() != PlayerState.FOLDED)
                .collect(Collectors.toList());
    }
}
