package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Player {
    private final static Logger logger = LoggerFactory.getLogger(Player.class);

    private String id;
    private List<Card> cards;
    // private int tablePosition;
    // private int chips;
    // private boolean bigBlindPosition = false;
    // private boolean smallBlindPosition = false;
    private HandRank handRank;
    private List<Card> bestCards;

    // public Player(String id, int tablePosition, int chips) {
    public Player(String id, int chips) {
        this.id = id;
        // this.tablePosition = tablePosition;
        // this.chips = chips;
        cards = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void acceptCard(Card card) {
        logger.info("Player: acceptCard: card: " + card);
        cards.add(card);
        logger.info("Player: acceptCard: cards.size(): " + cards.size());

        if (cards.size() == 7) {
            Collections.sort(cards, (card1, card2) -> Integer.compare(card1.getRank().getValue(),
                    card2.getRank().getValue()));
            Collections.reverse(cards);
            // cards = cards.reversed();
            AnalysisEngine.setHandRank(this);
        }
    }

    // public void discardCard(Card card) {
    // cards.remove(card);
    // }

    // public void playBigBlind(int bigBlind) {
    // chips -= bigBlind;
    // }

    // public void playSmallBlind(int smallBlind) {
    // chips -= smallBlind;
    // }

    // public int getTablePosition() {
    // return tablePosition;
    // }

    // public int getChips() {
    // return chips;
    // }

    // public List<Card> removeCards() {
    // List<Card> copy = new ArrayList<>();
    // Collections.copy(cards, copy);
    // cards.clear();
    // return copy;
    // }

    // public List<Card> getCardsInHand() {
    // // return Collections.unmodifiableList(cards);
    // return cards;
    // }

    public List<Card> getAllCards() {
        return cards;
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }

    public void play() {}

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Player [");
        builder.append("id: " + id + ", ");
        // builder.append("tablePosition: " + tablePosition + ", ");
        builder.append("handRank: " + handRank + ", ");
        builder.append("cards: ");
        int count = 0;
        for (Card card : cards) {
            count++;
            builder.append(card.toString());
            if (count < cards.size())
                builder.append(", ");
        }
        builder.append("]");
        return builder.toString();
    }

    // public void setBigBlindPosition(boolean value) {
    // bigBlindPosition = value;
    // }

    // public void setSmallBlindPosition(boolean value) {
    // smallBlindPosition = value;
    // }

    // public void setPlayerResult(PlayerResult playerResult) {
    // this.playerResult = playerResult;
    // }

    // public PlayerResult getPlayerResults() {
    // return playerResult;
    // }

    public HandRank getHandRank() {
        return handRank;
    }

    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }
}
