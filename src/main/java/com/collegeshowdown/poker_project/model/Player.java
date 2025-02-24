package com.collegeshowdown.poker_project.model;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.io.Serializable;

@Entity
public class Player implements Serializable {
    private final static Logger logger = LoggerFactory.getLogger(Player.class);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    private String name;
    private String email;
    private String university;
    private String imageURL;
    private List<Card> cards;
    private List<Card> bestCards;
    private HandRank handRank;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);
        this.cards = cards;

        // if (cards.size() == 7) {
        this.cards.addAll(Game.getInstance().getBoard());
        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);

        Collections.sort(cards, (card1, card2) -> Integer.compare(card1.getRank().getValue(),
                card2.getRank().getValue()));
        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);
        Collections.reverse(cards);
        logger.info(">>>>>>>>>>>>>>>>>>> setCards: " + cards);
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
    }

    public void sortCardsHighToLow() {
        Collections.sort(this.bestCards);
        Collections.reverse(this.bestCards);
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
