package com.turner.poker;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Player {
    private final static Logger logger = LoggerFactory.getLogger(Player.class);

    private String id;
    private List<Card> cards;
    private List<Card> bestCards;
    private HandRank handRank;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);
        this.cards = cards;

        // if (cards.size() == 7) {
        this.cards.addAll(Game.getInstance().getBoard());
        Collections.sort(cards, (card1, card2) -> Integer.compare(card1.getRank().getValue(),
                card2.getRank().getValue()));
        Collections.reverse(cards);
        AnalysisResults analysisResults = AnalysisEngine.analyzeHand(this);
        logger.info("analysisResults: " + analysisResults);
        setHandRank(analysisResults.handRank());
        setBestCards(analysisResults.bestCards());
        // }

        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);

    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
        Collections.sort(this.bestCards);
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }


    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("Player...");
        stringBuilder.append("\n...id: " + id);
        stringBuilder.append("\n...cards: " + cards);
        return stringBuilder.toString();
    }

}
