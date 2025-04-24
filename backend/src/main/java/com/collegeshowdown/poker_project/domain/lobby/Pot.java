package com.collegeshowdown.poker_project.domain.lobby;

import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.models.*;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Collectors;
import java.util.Collections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.io.Serializable;

public class Pot {
    public List<ConnectedPlayer> playersInPot;
    public Map<ConnectedPlayer, Integer> playerContributionsToPot;
    public int currentAmountInPot;
    public int chipsOfPlayerWithLeastAmount;
    public int minimumBetRequired;
    public int maxBetAllowed;
    public boolean closed;

    public Pot() {
    }



    public Pot(int chipsOfPlayerWithLeastAmount) {
        this.chipsOfPlayerWithLeastAmount = chipsOfPlayerWithLeastAmount;
    }



    public Pot(int minimumBetRequired, int chipsOfPlayerWithLeastAmount, List<ConnectedPlayer> playersInPot) {
        this.minimumBetRequired = minimumBetRequired;
        this.chipsOfPlayerWithLeastAmount = chipsOfPlayerWithLeastAmount;
        this.playersInPot = playersInPot;
        this.playerContributionsToPot = new HashMap<>();
    }



    public int getAmountInPot() {
        return currentAmountInPot;
    }



    public void addToAmountToPot(int newAmount) {
        currentAmountInPot = currentAmountInPot + newAmount;
    }



    public void emptyPot() {
        currentAmountInPot = 0;
    }



    public List<ConnectedPlayer> getPlayersInPot() {
        return playersInPot;
    }



    public int getMinimumBetRequired() {
        return this.minimumBetRequired;
    }



    public void setMinimumBetRequired(int minimumBetRequired) {
        this.minimumBetRequired = minimumBetRequired;
    }



    public int getChipsOfPlayerWithLeastAmount() {
        return this.chipsOfPlayerWithLeastAmount;
    }



    public void addPlayerContribution(ConnectedPlayer player, int contribution) {
        int currentContribution = 0;

        if (playerContributionsToPot.containsKey(player)) {
            currentContribution = playerContributionsToPot.get(player);
        }

        playerContributionsToPot.put(player, contribution + currentContribution);
    }



    public boolean isClosed() {
        return closed;
    }



    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public List<ConnectedPlayer> getEligiblePlayers() {
        return null; // TODO: implement
    }
}
