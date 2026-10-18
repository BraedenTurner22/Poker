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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

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

    // ─── Edge cases ──────────────────────────────────────────────────────────────

    private static List<ConnectedPlayer> winners(ConnectedPlayer... players) {
        return AnalysisEngine.getWinners(players);
    }

    @Test
    void wheelStraightLosesToSixHighStraight() {
        ConnectedPlayer wheel = player(
                c(ACE, HEARTS), c(TWO, SPADES), c(THREE, CLUBS), c(FOUR, DIAMONDS),
                c(FIVE, HEARTS), c(KING, SPADES), c(NINE, CLUBS));
        ConnectedPlayer sixHigh = player(
                c(SIX, HEARTS), c(TWO, CLUBS), c(THREE, DIAMONDS), c(FOUR, SPADES),
                c(FIVE, CLUBS), c(KING, HEARTS), c(NINE, DIAMONDS));
        assertEquals(List.of(sixHigh), winners(wheel, sixHigh));
    }

    @Test
    void wheelStraightFlushIsStraightFlushAndBeatsQuads() {
        ConnectedPlayer steelWheel = player(
                c(ACE, CLUBS), c(TWO, CLUBS), c(THREE, CLUBS), c(FOUR, CLUBS),
                c(FIVE, CLUBS), c(KING, SPADES), c(KING, HEARTS));
        assertEquals(HandRank.STRAIGHT_FLUSH, rankOf(steelWheel));
        ConnectedPlayer quads = player(
                c(KING, CLUBS), c(KING, DIAMONDS), c(KING, SPADES), c(KING, HEARTS),
                c(FIVE, DIAMONDS), c(TWO, HEARTS), c(THREE, SPADES));
        assertEquals(List.of(steelWheel), winners(steelWheel, quads));
    }

    @Test
    void flushPlusStraightInDifferentSuitsIsOnlyAFlush() {
        // 5-9 straight, but the five hearts don't form a run
        ConnectedPlayer p = player(
                c(NINE, HEARTS), c(EIGHT, HEARTS), c(SEVEN, HEARTS), c(SIX, SPADES),
                c(FIVE, CLUBS), c(TWO, HEARTS), c(KING, HEARTS));
        assertEquals(HandRank.FLUSH, rankOf(p));
    }

    @Test
    void straightFlushUsesOnlyFlushSuitCards() {
        // 4-8 spades straight flush plus an off-suit 9 that extends the plain straight
        ConnectedPlayer p = player(
                c(EIGHT, SPADES), c(SEVEN, SPADES), c(SIX, SPADES), c(FIVE, SPADES),
                c(FOUR, SPADES), c(NINE, HEARTS), c(TEN, CLUBS));
        assertEquals(HandRank.STRAIGHT_FLUSH, rankOf(p));
        assertTrue(p.getBestCards().stream().allMatch(card -> card.getSuit() == SPADES));
        assertEquals(EIGHT, p.getBestCards().get(0).getRank());
    }

    @Test
    void threePairsUsesTopTwoAndBestKicker() {
        // Board 8-8-5-5-K: Q-Q plays QQ88K, beating 8855 with an ace kicker
        Card[] board = { c(EIGHT, HEARTS), c(EIGHT, SPADES), c(FIVE, CLUBS),
                         c(FIVE, DIAMONDS), c(KING, HEARTS) };
        ConnectedPlayer queens = player(board[0], board[1], board[2], board[3], board[4],
                c(QUEEN, SPADES), c(QUEEN, CLUBS));
        ConnectedPlayer aceKicker = player(board[0], board[1], board[2], board[3], board[4],
                c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(List.of(queens), winners(queens, aceKicker));
        assertEquals(HandRank.TWO_PAIR, queens.getHandRank());
        // Third pair (fives) can't be used as two pair, but its rank still counts as kicker
        ConnectedPlayer threePairs = player(
                c(NINE, HEARTS), c(NINE, SPADES), c(SEVEN, CLUBS), c(SEVEN, DIAMONDS),
                c(SIX, HEARTS), c(SIX, SPADES), c(TWO, CLUBS));
        ConnectedPlayer fourKicker = player(
                c(NINE, CLUBS), c(NINE, DIAMONDS), c(SEVEN, HEARTS), c(SEVEN, SPADES),
                c(FOUR, HEARTS), c(THREE, SPADES), c(TWO, DIAMONDS));
        assertEquals(List.of(threePairs), winners(threePairs, fourKicker));
    }

    @Test
    void fullHouseWithTripsAndTwoPairsUsesHigherPair() {
        ConnectedPlayer tensFullOfKings = player(
                c(TEN, HEARTS), c(TEN, SPADES), c(TEN, CLUBS), c(KING, DIAMONDS),
                c(KING, HEARTS), c(FOUR, SPADES), c(FOUR, CLUBS));
        ConnectedPlayer tensFullOfQueens = player(
                c(TEN, DIAMONDS), c(TEN, HEARTS), c(TEN, SPADES), c(QUEEN, DIAMONDS),
                c(QUEEN, HEARTS), c(ACE, SPADES), c(TWO, CLUBS));
        assertEquals(List.of(tensFullOfKings), winners(tensFullOfKings, tensFullOfQueens));
    }

    @Test
    void twoTripsUseHigherAsTripsAndLowerAsPair() {
        ConnectedPlayer p = player(
                c(FOUR, HEARTS), c(FOUR, SPADES), c(FOUR, CLUBS), c(JACK, DIAMONDS),
                c(JACK, HEARTS), c(JACK, SPADES), c(TWO, CLUBS));
        assertEquals(HandRank.FULL_HOUSE, rankOf(p));
        assertEquals(JACK, p.getBestCards().get(0).getRank());
        assertEquals(FOUR, p.getBestCards().get(4).getRank());
    }

    @Test
    void quadsKickerDecides() {
        Card[] board = { c(NINE, HEARTS), c(NINE, SPADES), c(NINE, CLUBS),
                         c(NINE, DIAMONDS), c(TWO, HEARTS) };
        ConnectedPlayer ace = player(board[0], board[1], board[2], board[3], board[4],
                c(ACE, SPADES), c(THREE, CLUBS));
        ConnectedPlayer king = player(board[0], board[1], board[2], board[3], board[4],
                c(KING, SPADES), c(KING, CLUBS));
        assertEquals(List.of(ace), winners(ace, king));
    }

    @Test
    void flushWithSevenSuitedCardsUsesTopFive() {
        ConnectedPlayer p = player(
                c(TWO, HEARTS), c(FOUR, HEARTS), c(SIX, HEARTS), c(EIGHT, HEARTS),
                c(TEN, HEARTS), c(QUEEN, HEARTS), c(ACE, HEARTS));
        assertEquals(HandRank.FLUSH, rankOf(p));
        assertEquals(List.of(ACE, QUEEN, TEN, EIGHT, SIX),
                p.getBestCards().stream().map(Card::getRank).toList());
        // Shared 5-heart board: higher 6th heart in hand wins, lower ones don't play
        Card[] board = { c(TWO, HEARTS), c(FIVE, HEARTS), c(SEVEN, HEARTS),
                         c(NINE, HEARTS), c(JACK, HEARTS) };
        ConnectedPlayer kingHeart = player(board[0], board[1], board[2], board[3], board[4],
                c(KING, HEARTS), c(THREE, CLUBS));
        ConnectedPlayer threeHeart = player(board[0], board[1], board[2], board[3], board[4],
                c(THREE, HEARTS), c(ACE, CLUBS));
        assertEquals(List.of(kingHeart), winners(kingHeart, threeHeart));
    }

    @Test
    void sameHandDifferentSuitsSplits() {
        ConnectedPlayer a = player(
                c(ACE, HEARTS), c(KING, HEARTS), c(QUEEN, CLUBS), c(JACK, CLUBS),
                c(NINE, DIAMONDS), c(THREE, SPADES), c(TWO, SPADES));
        ConnectedPlayer b = player(
                c(ACE, SPADES), c(KING, DIAMONDS), c(QUEEN, CLUBS), c(JACK, CLUBS),
                c(NINE, DIAMONDS), c(THREE, SPADES), c(TWO, SPADES));
        assertEquals(2, winners(a, b).size());
    }

    @Test
    void fifthKickerCountsButSixthDoesNot() {
        Card[] board = { c(ACE, HEARTS), c(KING, SPADES), c(NINE, DIAMONDS),
                         c(SEVEN, CLUBS), c(FOUR, HEARTS) };
        // High card: A K 9 7 + best of hole; 6 beats 5 as fifth card
        ConnectedPlayer six = player(board[0], board[1], board[2], board[3], board[4],
                c(SIX, SPADES), c(TWO, CLUBS));
        ConnectedPlayer five = player(board[0], board[1], board[2], board[3], board[4],
                c(FIVE, SPADES), c(THREE, CLUBS));
        assertEquals(List.of(six), winners(six, five));
        // Both hole cards below the board's fifth card: board plays, split
        ConnectedPlayer low1 = player(board[0], board[1], board[2], board[3], board[4],
                c(THREE, SPADES), c(TWO, CLUBS));
        ConnectedPlayer low2 = player(board[0], board[1], board[2], board[3], board[4],
                c(THREE, DIAMONDS), c(TWO, HEARTS));
        assertEquals(2, winners(low1, low2).size());
    }

    // ─── Randomized cross-check against a brute-force reference ─────────────────

    /**
     * Deals thousands of random boards to several players and checks the
     * engine's category, best five cards and winner set against an
     * independent evaluator that scores every 5-card subset of the 7 cards.
     */
    @Test
    void randomHandsMatchBruteForceReference() {
        Random rng = new Random(20261018L);
        List<Card> deck = new ArrayList<>();
        for (Suit s : Suit.values()) for (Rank r : Rank.values()) deck.add(c(r, s));

        for (int trial = 0; trial < 20000; trial++) {
            Collections.shuffle(deck, rng);
            int n = 2 + rng.nextInt(7);
            List<Card> board = deck.subList(0, 5);
            ConnectedPlayer[] players = new ConnectedPlayer[n];
            long[] ref = new long[n];
            long best = -1;
            for (int i = 0; i < n; i++) {
                List<Card> seven = new ArrayList<>(board);
                seven.add(deck.get(5 + 2 * i));
                seven.add(deck.get(6 + 2 * i));
                Collections.shuffle(seven, rng);
                players[i] = player(seven.toArray(new Card[0]));
                ref[i] = referenceBest(seven);
                best = Math.max(best, ref[i]);

                HandRank engineRank = rankOf(players[i]);
                int engineCategory = engineRank == HandRank.ROYAL_FLUSH
                        ? HandRank.STRAIGHT_FLUSH.getValue() : engineRank.getValue();
                assertEquals((int) (ref[i] >> 20), engineCategory, "category mismatch for " + seven);
                // The reported best five must themselves score as the best hand
                List<Card> bestFive = players[i].getBestCards();
                assertEquals(5, bestFive.size());
                assertEquals(5, new HashSet<>(bestFive).size());
                assertTrue(seven.containsAll(bestFive));
                assertEquals(ref[i], referenceScore(bestFive), "best cards don't make the hand: " + bestFive);
            }
            List<ConnectedPlayer> expected = new ArrayList<>();
            for (int i = 0; i < n; i++) if (ref[i] == best) expected.add(players[i]);
            assertEquals(expected, AnalysisEngine.getWinners(players), "winner mismatch on trial " + trial);
        }
    }

    /** Best reference score over all 21 five-card subsets. */
    private static long referenceBest(List<Card> seven) {
        long best = -1;
        for (int skipA = 0; skipA < 7; skipA++) {
            for (int skipB = skipA + 1; skipB < 7; skipB++) {
                List<Card> five = new ArrayList<>();
                for (int i = 0; i < 7; i++) if (i != skipA && i != skipB) five.add(seven.get(i));
                best = Math.max(best, referenceScore(five));
            }
        }
        return best;
    }

    /**
     * Independent 5-card scorer: category (HandRank values, royal folded into
     * straight flush) in the top bits, then five 4-bit tiebreak ranks.
     */
    private static long referenceScore(List<Card> five) {
        int[] count = new int[15];
        boolean flush = true;
        for (Card card : five) {
            count[card.getRank().getValue()]++;
            if (card.getSuit() != five.get(0).getSuit()) flush = false;
        }
        // Ranks ordered by (multiplicity desc, rank desc)
        List<Integer> ordered = new ArrayList<>();
        for (int mult = 4; mult >= 1; mult--)
            for (int r = 14; r >= 2; r--) if (count[r] == mult) ordered.add(r);

        int straightHigh = 0;
        if (ordered.size() == 5) {
            if (ordered.get(0) - ordered.get(4) == 4) straightHigh = ordered.get(0);
            else if (ordered.equals(List.of(14, 5, 4, 3, 2))) straightHigh = 5;
        }
        int maxCount = count[ordered.get(0)];
        int category;
        List<Integer> tiebreak = ordered;
        if (straightHigh > 0 && flush) { category = 9; tiebreak = List.of(straightHigh); }
        else if (maxCount == 4) category = 8;
        else if (maxCount == 3 && ordered.size() == 2) category = 7;
        else if (flush) category = 6;
        else if (straightHigh > 0) { category = 5; tiebreak = List.of(straightHigh); }
        else if (maxCount == 3) category = 4;
        else if (maxCount == 2 && ordered.size() == 3) category = 3;
        else if (maxCount == 2) category = 2;
        else category = 1;

        long s = category;
        for (int i = 0; i < 5; i++) s = (s << 4) | (i < tiebreak.size() ? tiebreak.get(i) : 0);
        return s;
    }
}
