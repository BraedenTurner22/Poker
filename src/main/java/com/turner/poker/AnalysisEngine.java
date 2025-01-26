package com.turner.poker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalysisEngine {

    private final static Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    public static AnalysisResults checkForRoyalFlush(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        // Suit suit = player.getAllCards().get(0).getSuit();
        // if (player.getAllCards().get(0).getRank() == Rank.ACE
        // && player.getAllCards().get(1).getRank() == Rank.KING
        // && player.getAllCards().get(2).getRank() == Rank.QUEEN
        // && player.getAllCards().get(3).getRank() == Rank.JACK
        // && player.getAllCards().get(4).getRank() == Rank.TEN
        // && suit == player.getAllCards().get(1).getSuit()
        // && suit == player.getAllCards().get(2).getSuit()
        // && suit == player.getAllCards().get(3).getSuit()
        // && suit == player.getAllCards().get(4).getSuit()
        // && suit == player.getAllCards().get(5).getSuit())
        // return HandRank.ROYAL_FLUSH;
        // else {
        // return HandRank.NOTHING;
        // }


        SortedSet<Card> bestCards = new TreeSet<>();
        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.debug("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                logger.debug("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCardRand = player.getAllCards().get(i).getRank().getValue();
                int nextCardRank = player.getAllCards().get(i + 1).getRank().getValue();
                logger.debug("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.debug("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCardRand == nextCardRank + 1) {
                    consecutiveCardCount++;
                    bestCards.add(player.getAllCards().get(i));
                    bestCards.add(player.getAllCards().get(i + 1));
                }

                logger.debug("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == player.getAllCards().size())
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            player.setBestCards(bestCards);
            logger.info("returning HandRank.ROYAL_FLUSH");
            return new AnalysisResults(player.getId(), HandRank.ROYAL_FLUSH, bestCards);
        } else {
            logger.info("returning HandRank.NOTHING");
            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
        }
    }

    public static AnalysisResults checkForStraightFlush(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.debug("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                bestCards.clear();
                logger.info("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCardRank = player.getAllCards().get(i).getRank().getValue();
                int nextCardRank = player.getAllCards().get(i + 1).getRank().getValue();
                logger.debug("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.debug("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCardRank == nextCardRank + 1) {
                    consecutiveCardCount++;
                    bestCards.add(player.getAllCards().get(i));
                    bestCards.add(player.getAllCards().get(i + 1));
                }

                logger.debug("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == player.getAllCards().size())
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            for (Suit suit : Suit.values()) {
                int flushCount = 1;
                for (int i = 0; i < player.getAllCards().size(); i++) {
                    if (player.getAllCards().get(i).getSuit() == suit) {
                        flushCount++;
                    }
                    if (flushCount == 5) {
                        player.setBestCards(bestCards);
                        logger.info("returning HandRank.STRAIGHT_FLUSH");
                        return new AnalysisResults(player.getId(), HandRank.STRAIGHT_FLUSH, null);
                    }
                }
            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForFourOfAKind(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.debug("player.getAllCards().get(i).getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 3)
                break;

            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1).getRank()
                    && player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 2)
                            .getRank()
                    && player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 3)
                            .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                bestCards.add(player.getAllCards().get(i + 2));
                bestCards.add(player.getAllCards().get(i + 3));
                player.setBestCards(bestCards);
                logger.info("returning HandRank.FOUR_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.FOUR_OF_A_KIND, bestCards);
            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForFullHouse(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.debug("player.getAllCards().get(i).getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 2)
                break;

            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1).getRank()
                    && player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 2)
                            .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                bestCards.add(player.getAllCards().get(i + 2));

                logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
                logger.debug("player.getAllCards(): " + player.getAllCards());
                for (int j = 0; i < player.getAllCards().size(); j++) {
                    logger.debug("player.getAllCards().get(" + i + ").getRank(): "
                            + player.getAllCards().get(j).getRank());

                    if (i == player.getAllCards().size() - 1)
                        break;

                    if (player.getAllCards().get(j).getRank() == player.getAllCards().get(j + 1)
                            .getRank()) {
                        bestCards.add(player.getAllCards().get(j));
                        bestCards.add(player.getAllCards().get(j + 1));
                        player.setBestCards(bestCards);
                        logger.info("returning HandRank.FULL_HOUSE");
                        return new AnalysisResults(player.getId(), HandRank.FULL_HOUSE, bestCards);
                    }
                }
                logger.info("returning HandRank.NOTHING");
                return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);

    }

    public static AnalysisResults checkForFlush(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        int flushCount = 0;
        for (Suit suit : Suit.values()) {
            logger.debug("suit: " + suit);
            flushCount = 0;
            for (int i = 0; i < player.getAllCards().size(); i++) {
                bestCards.clear();
                if (player.getAllCards().get(i).getSuit() == suit) {
                    flushCount++;
                    bestCards.add(player.getAllCards().get(i));
                }
                logger.debug("flushCount: " + flushCount);
                if (flushCount == 5) {
                    player.setBestCards(bestCards);
                    logger.info("returning HandRank.FLUSH");
                    return new AnalysisResults(player.getId(), HandRank.FLUSH, bestCards);
                }
            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForStraight(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.debug("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                bestCards.clear();
                logger.info("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCardValue = player.getAllCards().get(i).getRank().getValue();
                int nextCardValue = player.getAllCards().get(i + 1).getRank().getValue();
                logger.debug("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.debug("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCardValue == nextCardValue + 1) {
                    consecutiveCardCount++;
                    bestCards.add(player.getAllCards().get(i));
                    bestCards.add(player.getAllCards().get(i + 1));
                }

                logger.debug("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == player.getAllCards().size())
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            player.setBestCards(bestCards);
            logger.info("returning HandRank.STRAIGHT");
            return new AnalysisResults(player.getId(), HandRank.STRAIGHT, bestCards);
        } else {
            logger.info("returning HandRank.NOTHING");
            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
        }
    }

    public static AnalysisResults checkForThreeOfAKind(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        SortedSet<Card> bestCards = new TreeSet<>();
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.debug("player.getAllCards().get(i).getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 2)
                break;
            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1).getRank()
                    && player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 2)
                            .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                bestCards.add(player.getAllCards().get(i + 2));
                player.setBestCards(bestCards);
                logger.info("returning HandRank.THREE_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.THREE_OF_A_KIND, bestCards);
            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForTwoPair(Player player) {
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());

        int pairCount = 0;
        SortedSet<Card> bestCards = new TreeSet<>();
        for (int i = 0; i < player.getAllCards().size(); i++) {
            if (i == player.getAllCards().size() - 1)
                break;
            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1)
                    .getRank()) {
                pairCount++;
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
            }
        }

        if (pairCount == 2) {
            player.setBestCards(bestCards);
            logger.info("returning HandRank.TWO_PAIR");
            return new AnalysisResults(player.getId(), HandRank.TWO_PAIR, bestCards);

        }

        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForOnePair(Player player) {
        SortedSet<Card> bestCards = new TreeSet<>();
        logger.debug("player.getAllCards().size(): " + player.getAllCards().size());
        logger.debug("player.getAllCards(): " + player.getAllCards());
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.debug("player.getAllCards().get(" + i + ").getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 1)
                break;

            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1)
                    .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                player.setBestCards(bestCards);
                logger.info("returning HandRank.ONE_PAIR");
                return new AnalysisResults(player.getId(), HandRank.ONE_PAIR, bestCards);

            }
        }
        logger.info("returning HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    public static AnalysisResults checkForHighCard(Player player) {
        Card highCard = player.getAllCards().get(player.getAllCards().size() - 1);
        SortedSet<Card> bestCards = new TreeSet<>();
        bestCards.add(highCard);
        player.setBestCards(bestCards);
        logger.info("returning HandRank.HIGH_CARD");
        return new AnalysisResults(player.getId(), HandRank.HIGH_CARD, bestCards);
    }

    public static AnalysisResults analyzeHand(Player player) {
        AnalysisResults analysisResults = AnalysisEngine.checkForRoyalFlush(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForStraightFlush(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForFourOfAKind(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForFullHouse(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForFlush(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForStraight(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForThreeOfAKind(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForTwoPair(player);
        if (analysisResults.handRank() == HandRank.NOTHING)
            analysisResults = AnalysisEngine.checkForOnePair(player);
        if (analysisResults.handRank() == HandRank.NOTHING) {
            analysisResults = AnalysisEngine.checkForHighCard(player);
        }
        return analysisResults;
    }

    private static Map<HandRank, List<String>> getHandRankToPlayerIdMap() {
        Map<HandRank, List<String>> handRankToPlayerIdMap = new LinkedHashMap<>();

        for (HandRank handRank : HandRank.values()) {
            handRankToPlayerIdMap.put(handRank, new ArrayList<>());
        }

        for (Player player : Players.getPlayers().values()) {
            switch (player.getHandRank()) {
                case HandRank.ROYAL_FLUSH:
                    handRankToPlayerIdMap.get(HandRank.ROYAL_FLUSH).add(player.getId());
                    break;
                case HandRank.STRAIGHT_FLUSH:
                    handRankToPlayerIdMap.get(HandRank.STRAIGHT_FLUSH).add(player.getId());
                    break;
                case HandRank.FOUR_OF_A_KIND:
                    handRankToPlayerIdMap.get(HandRank.FOUR_OF_A_KIND).add(player.getId());
                    break;
                case HandRank.FULL_HOUSE:
                    handRankToPlayerIdMap.get(HandRank.FULL_HOUSE).add(player.getId());
                    break;
                case HandRank.FLUSH:
                    handRankToPlayerIdMap.get(HandRank.FLUSH).add(player.getId());
                    break;
                case HandRank.STRAIGHT:
                    handRankToPlayerIdMap.get(HandRank.STRAIGHT).add(player.getId());
                    break;
                case HandRank.THREE_OF_A_KIND:
                    handRankToPlayerIdMap.get(HandRank.THREE_OF_A_KIND).add(player.getId());
                    break;
                case HandRank.TWO_PAIR:
                    handRankToPlayerIdMap.get(HandRank.TWO_PAIR).add(player.getId());
                    break;
                case HandRank.ONE_PAIR:
                    handRankToPlayerIdMap.get(HandRank.ONE_PAIR).add(player.getId());
                    break;
                case HandRank.HIGH_CARD:
                    handRankToPlayerIdMap.get(HandRank.HIGH_CARD).add(player.getId());
                    break;
                case HandRank.NOTHING:
                default:
                    handRankToPlayerIdMap.get(HandRank.NOTHING).add(player.getId());
                    break;
            }
        }

        return handRankToPlayerIdMap;

    }

    public static List<Winner> getWinners() {
        Map<HandRank, List<String>> handRankToPlayerIdMap = getHandRankToPlayerIdMap();
        logger.info("handRankToPlayerIdMap: " + handRankToPlayerIdMap);

        Map<HandRank, List<String>> reversedMap = new LinkedHashMap<>();

        List<Map.Entry<HandRank, List<String>>> entryList =
                new ArrayList<>(handRankToPlayerIdMap.entrySet());

        for (int i = entryList.size() - 1; i >= 0; i--) {
            Map.Entry<HandRank, List<String>> entry = entryList.get(i);
            reversedMap.put(entry.getKey(), entry.getValue());
        }

        boolean winnersFound = false;
        List<Winner> winners = new ArrayList<>();
        for (Map.Entry<HandRank, List<String>> entry : reversedMap.entrySet()) {
            if (!winnersFound && entry.getValue().size() > 0) {
                winnersFound = true;
                for (String id : entry.getValue()) {
                    winners.add(
                            new Winner(id, entry.getKey(), Players.getPlayer(id).getBestCards()));
                }
            }
        }

        return winners;
    }
}
