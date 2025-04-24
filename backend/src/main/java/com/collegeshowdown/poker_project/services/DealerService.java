package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Deck;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DealerService {
    private final static Logger logger = LoggerFactory.getLogger(DealerService.class);

    private Deck deck;

    public DealerService() {
        resetDeck();
    }



    public void resetDeck() {
        this.deck = new Deck();
    }



    /**
     * Deal 2 hole cards to each active player.
     */
    public void dealHoleCards(Lobby lobby) {
        logger.info("Dealing hole cards to {} players", lobby.tableCount());

        for (int i = 0; i < 2; i++) {
            for (ConnectedPlayer player : lobby.getPlayersAtTable()) {
                if (player != null && player.isActive()) {
                    List<Card> holeCards = new ArrayList<>();
                    holeCards.add(deck.dealCard());
                    player.addCards(holeCards);
                }
            }
        }
    }



    /**
     * Deal the flop (first 3 community cards).
     */
    public void dealFlop(Lobby lobby) {
        logger.info("Dealing the flop");
        List<Card> board = lobby.getBoard();

        // Burn a card
        deck.dealCard();

        // Deal 3 cards to the board
        for (int i = 0; i < 3; i++) {
            board.add(deck.dealCard());
        }
    }



    /**
     * Deal the turn (4th community card).
     */
    public void dealTurn(Lobby lobby) {
        logger.info("Dealing the turn");
        List<Card> board = lobby.getBoard();

        // Burn a card
        deck.dealCard();

        // Deal 1 card to the board
        board.add(deck.dealCard());
    }



    /**
     * Deal the river (5th community card).
     */
    public void dealRiver(Lobby lobby) {
        logger.info("Dealing the river");
        List<Card> board = lobby.getBoard();

        // Burn a card
        deck.dealCard();

        // Deal 1 card to the board
        board.add(deck.dealCard());
    }



    /**
     * Get the current deck
     */
    public Deck getDeck() {
        return deck;
    }
}