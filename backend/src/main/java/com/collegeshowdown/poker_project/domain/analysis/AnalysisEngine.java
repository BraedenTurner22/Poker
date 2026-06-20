package com.collegeshowdown.poker_project.domain.analysis;

import com.collegeshowdown.poker_project.domain.card.Card;
import com.collegeshowdown.poker_project.domain.card.Rank;
import com.collegeshowdown.poker_project.domain.card.Suit;
import com.collegeshowdown.poker_project.domain.player.ConnectedPlayer;
import com.collegeshowdown.poker_project.domain.player.HandRank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seven-card poker hand evaluator.
 *
 * Each hand is reduced to a single comparable long score:
 *   [ category (4 bits) | tiebreak ranks (5 × 4 bits, most significant first) ]
 * Two hands compare correctly by comparing their scores — categories first,
 * then kickers in order. Ties (equal scores) split the pot.
 */
public class AnalysisEngine {

    private final static Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Evaluates the player's cards (hole + board), storing the result on the
     * player: handRank for display and bestCards (the made 5-card hand,
     * highest-relevance first).
     */
    public static AnalysisResults analyzeHand(ConnectedPlayer player) {
        List<Card> cards = new ArrayList<>(player.getCards());
        cards.sort(byRankDesc());

        Evaluation eval = evaluate(cards);

        player.setHandRank(eval.handRank);
        player.setBestCards(eval.bestCards);

        logger.info("Player {} analyzed: {} ({})",
                player.playerRecord.getName(), eval.handRank,
                eval.bestCards);

        return new AnalysisResults(player.playerRecord.getId(), eval.handRank, eval.bestCards);
    }



    /**
     * Returns the player(s) holding the best hand. Players must contain their
     * hole + board cards; analysis runs (or re-runs) internally so callers
     * never see stale ranks. Multiple winners means a split pot.
     */
    public static List<ConnectedPlayer> getWinners(ConnectedPlayer[] players) {
        List<ConnectedPlayer> winners = new ArrayList<>();
        long bestScore = -1;

        for (ConnectedPlayer player : players) {
            List<Card> cards = new ArrayList<>(player.getCards());
            cards.sort(byRankDesc());
            Evaluation eval = evaluate(cards);

            // Keep display fields in sync
            player.setHandRank(eval.handRank);
            player.setBestCards(eval.bestCards);

            if (eval.score > bestScore) {
                bestScore = eval.score;
                winners.clear();
                winners.add(player);
            } else if (eval.score == bestScore) {
                winners.add(player);
            }
        }
        return winners;
    }

    // ─── Evaluation core ─────────────────────────────────────────────────────────

    /** The made hand: category, comparable score, and the five cards forming it. */
    record Evaluation(HandRank handRank, long score, List<Card> bestCards) {}

    /**
     * Evaluates 5–7 cards sorted descending by rank.
     */
    static Evaluation evaluate(List<Card> cards) {
        if (cards.size() < 5) {
            // Defensive: should never happen at showdown
            return new Evaluation(HandRank.HIGH_CARD,
                    score(HandRank.HIGH_CARD, ranksOf(cards)),
                    new ArrayList<>(cards));
        }

        // Rank counts and suit grouping
        int[] countByRank = new int[15];
        Map<Suit, List<Card>> bySuit = new EnumMap<>(Suit.class);
        for (Card c : cards) {
            countByRank[c.getRank().getValue()]++;
            bySuit.computeIfAbsent(c.getSuit(), s -> new ArrayList<>()).add(c);
        }

        List<Card> flushCards = bySuit.values().stream()
                .filter(list -> list.size() >= 5)
                .findFirst()
                .orElse(null);

        // ── Straight flush (incl. royal) ──
        if (flushCards != null) {
            int sfHigh = straightHigh(flushCards);
            if (sfHigh > 0) {
                List<Card> best = straightCards(flushCards, sfHigh);
                HandRank rank = sfHigh == 14 ? HandRank.ROYAL_FLUSH : HandRank.STRAIGHT_FLUSH;
                return new Evaluation(rank, score(rank, List.of(sfHigh)), best);
            }
        }

        // Ranks holding quads / trips / pairs, each list descending by rank
        List<Integer> quads = ranksWithCount(countByRank, 4);
        List<Integer> trips = ranksWithCount(countByRank, 3);
        List<Integer> pairs = ranksWithCount(countByRank, 2);

        // ── Four of a kind ──
        if (!quads.isEmpty()) {
            int quad = quads.get(0);
            int kicker = highestRankExcluding(cards, List.of(quad));
            List<Card> best = take(cards, quad, 4);
            best.add(firstOfRank(cards, kicker));
            return new Evaluation(HandRank.FOUR_OF_A_KIND,
                    score(HandRank.FOUR_OF_A_KIND, List.of(quad, kicker)), best);
        }

        // ── Full house (top trips + best remaining pair or second trips) ──
        if (!trips.isEmpty() && (trips.size() >= 2 || !pairs.isEmpty())) {
            int three = trips.get(0);
            int two = trips.size() >= 2
                    ? Math.max(trips.get(1), pairs.isEmpty() ? 0 : pairs.get(0))
                    : pairs.get(0);
            List<Card> best = take(cards, three, 3);
            best.addAll(take(cards, two, 2));
            return new Evaluation(HandRank.FULL_HOUSE,
                    score(HandRank.FULL_HOUSE, List.of(three, two)), best);
        }

        // ── Flush ──
        if (flushCards != null) {
            List<Card> best = new ArrayList<>(flushCards.stream()
                    .sorted(byRankDesc()).limit(5).toList());
            return new Evaluation(HandRank.FLUSH,
                    score(HandRank.FLUSH, ranksOf(best)), best);
        }

        // ── Straight ──
        int sHigh = straightHigh(cards);
        if (sHigh > 0) {
            List<Card> best = straightCards(cards, sHigh);
            return new Evaluation(HandRank.STRAIGHT, score(HandRank.STRAIGHT, List.of(sHigh)), best);
        }

        // ── Three of a kind ──
        if (!trips.isEmpty()) {
            int three = trips.get(0);
            List<Card> best = take(cards, three, 3);
            List<Integer> kickers = topKickers(cards, List.of(three), 2);
            kickers.forEach(k -> best.add(firstOfRank(cards, k)));
            List<Integer> tiebreak = new ArrayList<>(List.of(three));
            tiebreak.addAll(kickers);
            return new Evaluation(HandRank.THREE_OF_A_KIND,
                    score(HandRank.THREE_OF_A_KIND, tiebreak), best);
        }

        // ── Two pair (best two pairs + best kicker) ──
        if (pairs.size() >= 2) {
            int high = pairs.get(0), low = pairs.get(1);
            int kicker = highestRankExcluding(cards, List.of(high, low));
            List<Card> best = take(cards, high, 2);
            best.addAll(take(cards, low, 2));
            best.add(firstOfRank(cards, kicker));
            return new Evaluation(HandRank.TWO_PAIR,
                    score(HandRank.TWO_PAIR, List.of(high, low, kicker)), best);
        }

        // ── One pair ──
        if (pairs.size() == 1) {
            int pair = pairs.get(0);
            List<Card> best = take(cards, pair, 2);
            List<Integer> kickers = topKickers(cards, List.of(pair), 3);
            kickers.forEach(k -> best.add(firstOfRank(cards, k)));
            List<Integer> tiebreak = new ArrayList<>(List.of(pair));
            tiebreak.addAll(kickers);
            return new Evaluation(HandRank.ONE_PAIR, score(HandRank.ONE_PAIR, tiebreak), best);
        }

        // ── High card ──
        List<Card> best = new ArrayList<>(cards.subList(0, 5));
        return new Evaluation(HandRank.HIGH_CARD, score(HandRank.HIGH_CARD, ranksOf(best)), best);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private static Comparator<Card> byRankDesc() {
        return Comparator.comparingInt((Card c) -> c.getRank().getValue()).reversed();
    }

    /** Packs category + up to five tiebreak ranks into one comparable long. */
    private static long score(HandRank rank, List<Integer> tiebreaks) {
        long s = rank.getValue();
        for (int i = 0; i < 5; i++) {
            s = (s << 4) | (i < tiebreaks.size() ? tiebreaks.get(i) : 0);
        }
        return s;
    }

    /**
     * Highest straight top-rank among the given cards, or 0 if none.
     * Handles the wheel (A-2-3-4-5) by treating the ace as rank 1 too.
     */
    private static int straightHigh(List<Card> cards) {
        boolean[] present = new boolean[16];
        for (Card c : cards) present[c.getRank().getValue()] = true;
        present[1] = present[14]; // wheel ace

        for (int high = 14; high >= 5; high--) {
            boolean run = true;
            for (int r = high; r > high - 5; r--) {
                if (!present[r]) { run = false; break; }
            }
            if (run) return high;
        }
        return 0;
    }

    /** Collects one card per rank for the straight ending at {@code high}. */
    private static List<Card> straightCards(List<Card> cards, int high) {
        List<Card> best = new ArrayList<>();
        for (int r = high; r > high - 5; r--) {
            int want = (r == 1) ? 14 : r; // the wheel's "1" is the ace
            best.add(firstOfRank(cards, want));
        }
        return best;
    }

    /** Ranks appearing exactly {@code count} times, descending. */
    private static List<Integer> ranksWithCount(int[] countByRank, int count) {
        List<Integer> result = new ArrayList<>();
        for (int r = 14; r >= 2; r--) {
            if (countByRank[r] == count) result.add(r);
        }
        return result;
    }

    private static int highestRankExcluding(List<Card> cards, List<Integer> excluded) {
        return cards.stream()
                .map(c -> c.getRank().getValue())
                .filter(r -> !excluded.contains(r))
                .max(Integer::compare)
                .orElse(0);
    }

    /** The {@code n} highest distinct-from-excluded card ranks (kickers). */
    private static List<Integer> topKickers(List<Card> cards, List<Integer> excluded, int n) {
        return cards.stream()
                .map(c -> c.getRank().getValue())
                .filter(r -> !excluded.contains(r))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(n)
                .toList();
    }

    /** Up to {@code n} cards of the given rank. */
    private static List<Card> take(List<Card> cards, int rank, int n) {
        List<Card> result = new ArrayList<>();
        for (Card c : cards) {
            if (c.getRank().getValue() == rank && result.size() < n) result.add(c);
        }
        return result;
    }

    private static Card firstOfRank(List<Card> cards, int rank) {
        for (Card c : cards) {
            if (c.getRank().getValue() == rank) return c;
        }
        return cards.get(0); // unreachable for valid input
    }

    private static List<Integer> ranksOf(List<Card> cards) {
        return cards.stream().map(c -> c.getRank().getValue()).toList();
    }

    /** Convenience used by tests. */
    static Card card(Rank rank, Suit suit) {
        return new Card(rank, suit);
    }
}
