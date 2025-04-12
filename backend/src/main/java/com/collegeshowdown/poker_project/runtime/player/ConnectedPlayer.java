package com.collegeshowdown.poker_project.runtime.player;

import com.collegeshowdown.poker_project.model.PlayerRecord;
import com.collegeshowdown.poker_project.runtime.card.*;

import java.util.*;

public class ConnectedPlayer {
    public final PlayerRecord playerRecord;
    public final Object connection; // TODO - figure this out

    private List<Card> cards;
    private List<Card> bestCards;

    private HandRank handRank;

    private boolean isSmallBlind;
    private boolean isBigBlind;

    public ConnectedPlayer(PlayerRecord playerRecord, Object connection) {
        this.playerRecord = playerRecord;
        this.connection = connection;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }

    public List<Card> getCards() {
        return cards;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }

    public HandRank getHandRank() {
        return handRank;
    }
}
