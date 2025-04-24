package com.collegeshowdown.poker_project.domain.player;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.lobby.Pot;
import com.collegeshowdown.poker_project.models.PlayerRecord;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ConnectedPlayer {

    public final PlayerRecord playerRecord;
    public final Object connection; // TODO: wire in your WebSocket/session object

    private List<Card> cards = new ArrayList<>();
    private List<Card> bestCards = new ArrayList<>();
    private int chipsActivelyUsed;
    private PlayerState playerState = PlayerState.ACTIVE;
    private HandRank handRank;

    public ConnectedPlayer(PlayerRecord playerRecord, Object connection) {
        this.playerRecord = playerRecord;
        this.connection = connection;
    }

    // ─── State accessors ─────────────────────────────────────────────────────────



    public PlayerState getPlayerState() {
        return playerState;
    }



    public void setPlayerState(PlayerState playerState) {
        this.playerState = playerState;
    }



    public boolean isActive() {
        return this.playerState == PlayerState.ACTIVE;
    }

    // ─── Card methods ────────────────────────────────────────────────────────────



    public void addCards(List<Card> cards) {
        this.cards.addAll(cards);
    }



    public void setCards(List<Card> cards) {
        this.cards = cards;
    }



    public List<Card> getCards() {
        return cards;
    }



    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }



    public List<Card> getBestCards() {
        return bestCards;
    }



    public Card getBestCardAtIndex(int index) {
        return bestCards.get(index);
    }

    // ─── Hand rank ───────────────────────────────────────────────────────────────



    public HandRank getHandRank() {
        return handRank;
    }



    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }

    // ─── Betting actions ─────────────────────────────────────────────────────────



    public int payBlind(int blindAmount, Pot currentPot) {
        deductChips(blindAmount, currentPot);
        checkAllIn();
        return blindAmount;
    }



    public int call(int callAmount, Pot currentPot) {
        deductChips(callAmount, currentPot);
        checkAllIn();
        return callAmount;
    }



    public int raise(int raiseAmount, Pot currentPot) {
        deductChips(raiseAmount, currentPot);
        checkAllIn();
        return raiseAmount;
    }



    private void deductChips(int amount, Pot pot) {
        this.chipsActivelyUsed -= amount;
        pot.addToAmountToPot(amount);
        pot.addPlayerContribution(this, amount);
    }



    private void checkAllIn() {
        if (chipsActivelyUsed <= 0) {
            this.playerState = PlayerState.ALL_IN;
        }
    }

    // ─── Folding & winning ───────────────────────────────────────────────────────



    public void foldCards(List<Pot> allActivePots) {
        this.playerState = PlayerState.FOLDED;
        this.cards.clear();
        this.bestCards.clear();
        this.handRank = null;

        for (Pot pot : allActivePots) {
            pot.getPlayersInPot().remove(this);
        }
    }



    public void winPot(Pot currentPot) {
        int winnings = currentPot.getAmountInPot();
        this.chipsActivelyUsed += winnings;
        currentPot.addToAmountToPot(winnings);
    }

    // ─── Chips getter ───────────────────────────────────────────────────────────



    public int getActiveChips() {
        return chipsActivelyUsed;
    }
}
