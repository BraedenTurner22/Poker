package com.collegeshowdown.poker_project;

import com.collegeshowdown.poker_project.domain.analysis.AnalysisEngine;
import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.HandRank;
import com.collegeshowdown.poker_project.models.PlayerRecord;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static com.collegeshowdown.poker_project.domain.card.Rank.*;
import static com.collegeshowdown.poker_project.domain.card.Suit.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hand-ranking correctness: every category, kicker rules, and split pots.
 * Each player's card list is hole + board (7 cards), unsorted on purpose —
 * the engine must not depend on input order.
 */
class HandEvaluatorTest {

    private static int nextId = 1;

    private static ConnectedPlayer player(Card... cards) {
        PlayerRecord record = new PlayerRecord("p" + nextId, "p" + nextId + "@umass.edu",
                "x", "umass.edu", "UMass", "LOCAL");
        try {
            Field f = PlayerRecord.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(record, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ConnectedPlayer p = new ConnectedPlayer(record);
        p.setCards(new ArrayList<>(List.of(cards)));
        return p;
    }

    private static Card c(Rank r, Suit s) { return new Card(r, s); }

    private static HandRank rankOf(ConnectedPlayer p) {
        AnalysisEngine.analyzeHand(p);
        return p.getHandRank();
    }

    // ─── Category detection ──────────────────────────────────────────────────────

    @Test
    void detectsRoyalFlush() {
        ConnectedPlayer p = player(
                c(ACE, SPADES), c(KING, SPADES), c(QUEEN, SPADES), c(JACK, SPADES),
                c(TEN, SPADES), c(TWO, HEARTS), c(THREE, CLUBS));
        assertEquals(HandRank.ROYAL_FLUSH, rankOf(p));
    }

    @Test
    void detectsStraightFlush() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(EIGHT, HEARTS), c(SEVEN, HEARTS), c(SIX, HEARTS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(ACE, CLUBS));
        assertEquals(HandRank.STRAIGHT_FLUSH, rankOf(p));
    }

    @Test
    void detectsFourOfAKind() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(NINE, DIAMONDS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.FOUR_OF_A_KIND, rankOf(p));
    }

    @Test
    void detectsFullHouse() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(FIVE, DIAMONDS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.FULL_HOUSE, rankOf(p));
    }

    @Test
    void twoTripsFormFullHouse() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(FIVE, DIAMONDS),
                c(FIVE, HEARTS), c(FIVE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.FULL_HOUSE, rankOf(p));
    }

    @Test
    void detectsFlush() {
        ConnectedPlayer p = player(
                c(ACE, DIAMONDS), c(JACK, DIAMONDS), c(EIGHT, DIAMONDS), c(SIX, DIAMONDS),
                c(TWO, DIAMONDS), c(KING, SPADES), c(KING, CLUBS));
        assertEquals(HandRank.FLUSH, rankOf(p));
    }

    @Test
    void detectsStraight() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(EIGHT, SPADES), c(SEVEN, CLUBS), c(SIX, DIAMONDS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.STRAIGHT, rankOf(p));
    }

    @Test
    void straightDetectedDespitePairedBoard() {
        // The old adjacent-card scan broke when a pair interrupted the run
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(EIGHT, SPADES), c(EIGHT, CLUBS), c(SEVEN, CLUBS),
                c(SIX, DIAMONDS), c(FIVE, HEARTS), c(TWO, CLUBS));
        assertEquals(HandRank.STRAIGHT, rankOf(p));
    }

    @Test
    void detectsWheelStraight() {
        // A-2-3-4-5: the ace plays low
        ConnectedPlayer p = player(
                c(ACE, HEARTS), c(TWO, SPADES), c(THREE, CLUBS), c(FOUR, DIAMONDS),
                c(FIVE, HEARTS), c(NINE, SPADES), c(KING, CLUBS));
        assertEquals(HandRank.STRAIGHT, rankOf(p));
    }

    @Test
    void detectsThreeOfAKind() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(KING, DIAMONDS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.THREE_OF_A_KIND, rankOf(p));
    }

    @Test
    void detectsTwoPair() {
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(FIVE, CLUBS), c(FIVE, DIAMONDS),
                c(KING, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.TWO_PAIR, rankOf(p));
    }

    @Test
    void detectsOnePairRegardlessOfCardOrder() {
        // Pair split far apart in the input list — order must not matter
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(ACE, SPADES), c(FIVE, CLUBS), c(KING, DIAMONDS),
                c(TWO, HEARTS), c(JACK, SPADES), c(NINE, CLUBS));
        assertEquals(HandRank.ONE_PAIR, rankOf(p));
    }

    @Test
    void noMadeHandIsHighCardNeverNull() {
        // Regression: checkForTwoPair's no-match path used to corrupt the result
        // and leave handRank null, crashing every showdown
        ConnectedPlayer p = player(
                c(ACE, HEARTS), c(JACK, SPADES), c(NINE, CLUBS), c(SEVEN, DIAMONDS),
                c(FIVE, HEARTS), c(FOUR, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.HIGH_CARD, rankOf(p));
        assertNotNull(p.getHandRank());
        assertEquals(5, p.getBestCards().size());
    }

    // ─── Winner comparison ───────────────────────────────────────────────────────

    @Test
    void pairBeatsHighCard() {
        ConnectedPlayer pair = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(KING, CLUBS), c(SEVEN, DIAMONDS),
                c(FIVE, HEARTS), c(FOUR, SPADES), c(TWO, CLUBS));
        ConnectedPlayer high = player(
                c(ACE, HEARTS), c(JACK, SPADES), c(NINE, CLUBS), c(SEVEN, HEARTS),
                c(FIVE, SPADES), c(FOUR, CLUBS), c(TWO, DIAMONDS));
        List<ConnectedPlayer> winners =
                AnalysisEngine.getWinners(new ConnectedPlayer[]{pair, high});
        assertEquals(List.of(pair), winners);
    }

    @Test
    void higherPairWins() {
        ConnectedPlayer kings = player(
                c(KING, HEARTS), c(KING, SPADES), c(NINE, CLUBS), c(SEVEN, DIAMONDS),
                c(FIVE, HEARTS), c(FOUR, SPADES), c(TWO, CLUBS));
        ConnectedPlayer nines = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(ACE, CLUBS), c(SEVEN, HEARTS),
                c(FIVE, SPADES), c(FOUR, CLUBS), c(TWO, DIAMONDS));
        assertEquals(List.of(kings),
                AnalysisEngine.getWinners(new ConnectedPlayer[]{kings, nines}));
    }

    @Test
    void kickerDecidesBetweenEqualPairs() {
        // Both pair the board nines; ace kicker beats king kicker
        Card[] board = { c(NINE, HEARTS), c(NINE, SPADES), c(SEVEN, DIAMONDS),
                         c(FIVE, CLUBS), c(TWO, HEARTS) };
        ConnectedPlayer aceKicker = player(
                board[0], board[1], board[2], board[3], board[4],
                c(ACE, SPADES), c(THREE, CLUBS));
        ConnectedPlayer kingKicker = player(
                board[0], board[1], board[2], board[3], board[4],
                c(KING, SPADES), c(FOUR, CLUBS));
        assertEquals(List.of(aceKicker),
                AnalysisEngine.getWinners(new ConnectedPlayer[]{aceKicker, kingKicker}));
    }

    @Test
    void identicalHandsSplitThePot() {
        Card[] board = { c(ACE, HEARTS), c(KING, SPADES), c(QUEEN, DIAMONDS),
                         c(NINE, CLUBS), c(FIVE, HEARTS) };
        ConnectedPlayer a = player(
                board[0], board[1], board[2], board[3], board[4],
                c(TWO, SPADES), c(THREE, CLUBS));
        ConnectedPlayer b = player(
                board[0], board[1], board[2], board[3], board[4],
                c(TWO, HEARTS), c(THREE, DIAMONDS));
        List<ConnectedPlayer> winners =
                AnalysisEngine.getWinners(new ConnectedPlayer[]{a, b});
        assertEquals(2, winners.size());
    }

    @Test
    void flushBeatsStraight() {
        ConnectedPlayer flush = player(
                c(ACE, DIAMONDS), c(JACK, DIAMONDS), c(EIGHT, DIAMONDS), c(SIX, DIAMONDS),
                c(TWO, DIAMONDS), c(KING, SPADES), c(QUEEN, CLUBS));
        ConnectedPlayer straight = player(
                c(NINE, HEARTS), c(EIGHT, SPADES), c(SEVEN, CLUBS), c(SIX, HEARTS),
                c(FIVE, SPADES), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(List.of(flush),
                AnalysisEngine.getWinners(new ConnectedPlayer[]{flush, straight}));
    }

    @Test
    void fullHouseBeatsFlush() {
        ConnectedPlayer boat = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS), c(FIVE, DIAMONDS),
                c(FIVE, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        ConnectedPlayer flush = player(
                c(ACE, DIAMONDS), c(JACK, DIAMONDS), c(EIGHT, DIAMONDS), c(SIX, DIAMONDS),
                c(TWO, DIAMONDS), c(KING, SPADES), c(QUEEN, CLUBS));
        assertEquals(List.of(boat),
                AnalysisEngine.getWinners(new ConnectedPlayer[]{boat, flush}));
    }

    @Test
    void higherTwoPairWins() {
        ConnectedPlayer acesUp = player(
                c(ACE, HEARTS), c(ACE, SPADES), c(THREE, CLUBS), c(THREE, DIAMONDS),
                c(NINE, HEARTS), c(SEVEN, SPADES), c(TWO, CLUBS));
        ConnectedPlayer kingsUp = player(
                c(KING, HEARTS), c(KING, SPADES), c(QUEEN, CLUBS), c(QUEEN, DIAMONDS),
                c(NINE, SPADES), c(SEVEN, CLUBS), c(TWO, DIAMONDS));
        assertEquals(List.of(acesUp),
                AnalysisEngine.getWinners(new ConnectedPlayer[]{acesUp, kingsUp}));
    }
}
