package com.collegeshowdown.poker_project.model;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.Column;
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
    @Column(nullable = false, updatable = false, unique = true)
    private int id;
    private String name;
    private String email;
    private String university;
    private String imageURL;
    private List<Card> cards;
    private List<Card> bestCards;
    private HandRank handRank;
    private boolean isSmallBlind;
    private boolean isBigBlind;

    public Player() {}

    public Player(int id, String name, String email, String university, String imageURL,
            List<Card> cards, List<Card> bestCards, HandRank handRank, boolean isSmallBlind, boolean isBigBlind) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.university = university;
        this.imageURL = imageURL;
        this.cards = cards;
        this.bestCards = bestCards;
        this.handRank = handRank;
        this.isSmallBlind = isSmallBlind;
        this.isBigBlind = isBigBlind;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
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
        logger.info("Setting cards for player {}: {}", this.name, cards);
        this.cards = new ArrayList<>(cards);  // Create a defensive copy
        
        // Only add board cards if we don't already have 7 cards
        if (cards.size() < 7 && Game.getInstance() != null && Game.getInstance().getBoard() != null) {
            List<Card> boardCards = Game.getInstance().getBoard();
            for (Card boardCard : boardCards) {
                if (!this.cards.contains(boardCard)) {
                    this.cards.add(boardCard);
                }
            }
        }
        
        // Sort cards by rank (highest to lowest)
        Collections.sort(this.cards, (card1, card2) -> 
                Integer.compare(card2.getRank().getValue(), card1.getRank().getValue()));
        
        // Analyze the hand
        if (this.cards.size() >= 5) {
            AnalysisResults analysisResults = AnalysisEngine.analyzeHand(this);
            logger.info("Analysis results for player {}: {}", this.name, analysisResults);
            setHandRank(analysisResults.handRank());
            setBestCards(analysisResults.bestCards());
        } else {
            logger.warn("Not enough cards to analyze hand for player {}", this.name);
        }
    }

    public List<Card> getBestCards() {
        return bestCards;
    }

    public void setBestCards(List<Card> bestCards) {
        this.bestCards = bestCards;
    }


    public HandRank getHandRank() {
        return handRank;
    }

    public void setHandRank(HandRank handRank) {
        this.handRank = handRank;
    }

    public boolean getSmallBlindStatus() {
        return isSmallBlind;
    }

    public void setSmallBlindStatus(boolean smallBlindStatus) {
        this.isSmallBlind = smallBlindStatus;
    }    

    public boolean getBigBlindStatus() {
        return isBigBlind; 
    }

    public void setBigBlindStatus(boolean bigBlindStatus) {
        this.isBigBlind = bigBlindStatus;
    }    
    public void sortCardsHighToLow() {
        Collections.sort(this.bestCards);
        Collections.reverse(this.bestCards);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("Player{");
        stringBuilder.append("\nid = " + id);
        stringBuilder.append("\nname = " + name);
        stringBuilder.append("\nemail = " + email);
        stringBuilder.append("\nuniversity = " + university);
        stringBuilder.append("\nimageURL = " + imageURL);
        stringBuilder.append("\ncards = " + cards);
        stringBuilder.append("\nbestCards = " + bestCards);
        stringBuilder.append("\nhandRank = " + handRank);
        stringBuilder.append("\n}");
        return stringBuilder.toString();
    }
}
