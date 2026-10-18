package com.collegeshowdown.poker_project.services;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.PlayerState;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DealerService {
    private final static Logger logger = LoggerFactory.getLogger(DealerService.class);

    /**
     * Deal 2 hole cards to each player in the hand. Blinds are posted before
     * the deal, so this includes a blind poster who is already ALL_IN; only
     * FOLDED seats (busted players sitting the hand out) are skipped.
     */
    public void dealHoleCards(Lobby lobby) {
        logger.info("Dealing hole cards to {} players", lobby.tableCount());

        for (int i = 0; i < 2; i++) {
            for (ConnectedPlayer player : lobby.getPlayersAtTable()) {
                if (player != null && player.getPlayerState() != PlayerState.FOLDED) {
                    List<Card> holeCards = new ArrayList<>();
                    holeCards.add(lobby.getDeck().dealCard());
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

        lobby.getDeck().dealCard(); // burn
        for (int i = 0; i < 3; i++) {
            board.add(lobby.getDeck().dealCard());
        }
    }



    /**
     * Deal the turn (4th community card).
     */
    public void dealTurn(Lobby lobby) {
        logger.info("Dealing the turn");
        lobby.getDeck().dealCard(); // burn
        lobby.getBoard().add(lobby.getDeck().dealCard());
    }



    /**
     * Deal the river (5th community card).
     */
    public void dealRiver(Lobby lobby) {
        logger.info("Dealing the river");
        lobby.getDeck().dealCard(); // burn
        lobby.getBoard().add(lobby.getDeck().dealCard());
    }
}
