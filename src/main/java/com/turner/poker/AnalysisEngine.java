package com.turner.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalysisEngine {
    private final static Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    // ==========================================================================================
    // public static AnalysisResults checkForRoyalFlushV2(Player player) {
    // AnalysisResults analysisResultsForFlushCheck = null;

    // analysisResultsForFlushCheck = AnalysisEngine.checkForFlush(player);
    // if (analysisResultsForFlushCheck.handRank() == HandRank.FLUSH) {
    // }
    // logger.info("return HandRank.NOTHING");
    // return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    // }

    // ==========================================================================================
    public static AnalysisResults checkForRoyalFlush(Player player) {
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        // Suit suit = player.getCards().get(0).getSuit();
        // if (player.getCards().get(0).getRank() == Rank.ACE
        // && player.getCards().get(1).getRank() == Rank.KING
        // && player.getCards().get(2).getRank() == Rank.QUEEN
        // && player.getCards().get(3).getRank() == Rank.JACK
        // && player.getCards().get(4).getRank() == Rank.TEN
        // && suit == player.getCards().get(1).getSuit()
        // && suit == player.getCards().get(2).getSuit()
        // && suit == player.getCards().get(3).getSuit()
        // && suit == player.getCards().get(4).getSuit()
        // && suit == player.getCards().get(5).getSuit())
        // return HandRank.ROYAL_FLUSH;
        // else {
        // return HandRank.NOTHING;
        // }

        List<Card> bestCards = new ArrayList<>();
        int consecutiveCardCount = 1;
        int size = player.getCards().size();
        for (int pass = 0; pass < 3; pass++) {
            logger.debug("\n");
            logger.debug("pass " + pass);
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            for (int i = pass; i < pass + 5; i++) {
                logger.debug("i: " + i);

                if (player.getCards().get(i).getRank() != Rank.ACE)
                    continue;

                if (i == size - 1)
                    break;

                int currentCardRankValue = player.getCards().get(i).getRank().getValue();
                int nextCardRankValue = player.getCards().get(i + 1).getRank().getValue();
                logger.debug("currentCard: " + player.getCards().get(i).getRank().getValue());
                logger.debug("nextCard: " + player.getCards().get(i + 1).getRank().getValue());

                if (currentCardRankValue == nextCardRankValue + 1) {
                    consecutiveCardCount++;
                    bestCards.add(player.getCards().get(i));
                    bestCards.add(player.getCards().get(i + 1));
                }

                logger.debug("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == size)
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            player.setBestCards(bestCards);
            logger.info("return HandRank.ROYAL_FLUSH");
            return new AnalysisResults(player.getId(), HandRank.ROYAL_FLUSH, bestCards);
        } else {
            logger.info("return HandRank.NOTHING");
            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
        }
    }

    // ==========================================================================================
    public static AnalysisResults checkForStraightFlush(Player player) {
        int size = player.getCards().size();
        AnalysisResults analysisResultsForStraightCheck = null;
        analysisResultsForStraightCheck = AnalysisEngine.checkForStraight(player);
        if (analysisResultsForStraightCheck.handRank() == HandRank.STRAIGHT) {
            for (Suit suit : Suit.values()) {
                logger.debug("suit: " + suit);
                int flushCount = 0;
                for (int i = 0; i < size; i++) {
                    if (player.getCards().get(i).getSuit() == suit) {
                        flushCount++;
                    }

                    logger.debug("flushCount: " + flushCount);

                    if (flushCount == 5) {
                        player.setBestCards(analysisResultsForStraightCheck.bestCards());
                        logger.info("return HandRank.STRAIGHT_FLUSH");
                        return new AnalysisResults(player.getId(), HandRank.STRAIGHT_FLUSH,
                                player.getBestCards());
                    }
                }
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForFourOfAKind(Player player) {
        int size = player.getCards().size();

        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("rank: " + player.getCards().get(i).getRank());

            if (i == player.getCards().size() - 3)
                break;

            if (player.getCards().get(i).getRank() == player.getCards().get(i + 1).getRank()
                    && player.getCards().get(i).getRank() == player.getCards().get(i + 2).getRank()
                    && player.getCards().get(i).getRank() == player.getCards().get(i + 3)
                            .getRank()) {
                bestCards.add(player.getCards().get(i));
                bestCards.add(player.getCards().get(i + 1));
                bestCards.add(player.getCards().get(i + 2));
                bestCards.add(player.getCards().get(i + 3));
                player.setBestCards(bestCards);
                logger.info("return HandRank.FOUR_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.FOUR_OF_A_KIND, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForFullHouseV2(Player player) {
        AnalysisResults analysisResultsForThreeOfAKindCheck = null;
        AnalysisResults analysisResultsForOnePairCheck = null;

        analysisResultsForThreeOfAKindCheck = AnalysisEngine.checkForThreeOfAKind(player);
        if (analysisResultsForThreeOfAKindCheck.handRank() == HandRank.THREE_OF_A_KIND) {
            analysisResultsForOnePairCheck = AnalysisEngine.checkForOnePair(player);
            if (analysisResultsForOnePairCheck.handRank() == HandRank.ONE_PAIR) {
                List<Card> bestCards = new ArrayList<>();
                bestCards.addAll(analysisResultsForThreeOfAKindCheck.bestCards());
                bestCards.addAll(analysisResultsForOnePairCheck.bestCards());
                player.setBestCards(bestCards);
                logger.info("return HandRank.FULL_HOUSE");
                return new AnalysisResults(player.getId(), HandRank.FULL_HOUSE, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForFullHouse(Player player) {
        int size = player.getCards().size();

        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("i: " + i);
            logger.debug("rank: " + player.getCards().get(i).getRank());

            if (i == size - 2)
                break;
            if (player.getCards().get(i).getRank() == player.getCards().get(i + 1).getRank()
                    && player.getCards().get(i).getRank() == player.getCards().get(i + 2)
                            .getRank()) {

                bestCards.add(player.getCards().get(i));
                bestCards.add(player.getCards().get(i + 1));
                bestCards.add(player.getCards().get(i + 2));

                Rank threeOfAKindRank = player.getCards().get(i).getRank();

                for (int j = 0; j < size; j++) {
                    logger.debug("j: " + j);

                    if (j == size - 1) {
                        logger.info("return HandRank.NOTHING");
                        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
                    }

                    logger.debug("cards[" + j + "].rank: " + player.getCards().get(j).getRank());

                    if (player.getCards().get(j).getRank() == player.getCards().get(j + 1)
                            .getRank()) {
                        if (player.getCards().get(j).getRank() == threeOfAKindRank) {
                            logger.info("return HandRank.NOTHING");
                            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
                        }

                        bestCards.add(player.getCards().get(j));
                        bestCards.add(player.getCards().get(j + 1));
                        player.setBestCards(bestCards);
                        logger.info("return HandRank.FULL_HOUSE");
                        return new AnalysisResults(player.getId(), HandRank.FULL_HOUSE, bestCards);
                    }
                }
                // logger.info("return HandRank.NOTHING");
                // return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);

    }

    // ==========================================================================================
    public static AnalysisResults checkForFlush(Player player) {
        int size = player.getCards().size();

        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        int flushCount = 0;
        for (Suit suit : Suit.values()) {
            logger.debug("suit: " + suit);
            bestCards.clear();
            flushCount = 0;
            for (int i = 0; i < size; i++) {
                if (player.getCards().get(i).getSuit() == suit) {
                    flushCount++;
                    bestCards.add(player.getCards().get(i));
                }
                logger.debug("flushCount: " + flushCount);
                if (flushCount == 5) {
                    player.setBestCards(bestCards);
                    logger.info("return HandRank.FLUSH");
                    return new AnalysisResults(player.getId(), HandRank.FLUSH, bestCards);
                }
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForStraight(Player player) {
        int size = player.getCards().size();

        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.debug("\n");
            logger.debug("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                bestCards.clear();
                logger.debug("i: " + i);

                if (i == size - 1)
                    break;

                int currentCardValue = player.getCards().get(i).getRank().getValue();
                int nextCardValue = player.getCards().get(i + 1).getRank().getValue();
                logger.debug("currentCard: " + player.getCards().get(i).getRank().getValue());
                logger.debug("nextCard: " + player.getCards().get(i + 1).getRank().getValue());

                if (currentCardValue == nextCardValue + 1) {
                    consecutiveCardCount++;
                    bestCards.add(player.getCards().get(i));
                    bestCards.add(player.getCards().get(i + 1));
                }

                logger.debug("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == size)
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            player.setBestCards(bestCards);
            logger.info("return HandRank.STRAIGHT");
            return new AnalysisResults(player.getId(), HandRank.STRAIGHT, bestCards);
        } else {
            logger.info("return HandRank.NOTHING");
            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
        }
    }

    // ==========================================================================================
    public static AnalysisResults checkForThreeOfAKind(Player player) {
        int size = player.getCards().size();
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("rank: " + player.getCards().get(i).getRank());

            if (i == size - 2)
                break;
            if (player.getCards().get(i).getRank() == player.getCards().get(i + 1).getRank()
                    && player.getCards().get(i).getRank() == player.getCards().get(i + 2)
                            .getRank()) {
                bestCards.add(player.getCards().get(i));
                bestCards.add(player.getCards().get(i + 1));
                bestCards.add(player.getCards().get(i + 2));
                player.setBestCards(bestCards);
                logger.info("return HandRank.THREE_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.THREE_OF_A_KIND, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForTwoPair(Player player) {

        int size = player.getCards().size();
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        int pairCount = 0;
        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (i == size - 1)
                break;
            if (player.getCards().get(i).getRank() == player.getCards().get(i + 1).getRank()) {
                pairCount++;
                bestCards.add(player.getCards().get(i));
                bestCards.add(player.getCards().get(i + 1));
            }
        }

        if (pairCount == 2) {
            player.setBestCards(bestCards);
            logger.info("return HandRank.TWO_PAIR");
            return new AnalysisResults(player.getId(), HandRank.TWO_PAIR, bestCards);

        }

        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForOnePair(Player player) {
        int size = player.getCards().size();
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("cards[" + i + "].rank: " + player.getCards().get(i).getRank());

            if (i == player.getCards().size() - 1)
                break;

            if (player.getCards().get(i).getRank() == player.getCards().get(i + 1).getRank()) {
                bestCards.add(player.getCards().get(i));
                bestCards.add(player.getCards().get(i + 1));
                player.setBestCards(bestCards);
                logger.info("return HandRank.ONE_PAIR");
                return new AnalysisResults(player.getId(), HandRank.ONE_PAIR, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    public static AnalysisResults checkForHighCard(Player player) {
        int size = player.getCards().size();
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        Card highCard = player.getCards().get(size - 1);
        List<Card> bestCards = new ArrayList<>();
        bestCards.add(highCard);
        player.setBestCards(bestCards);
        logger.info("return HandRank.HIGH_CARD");
        return new AnalysisResults(player.getId(), HandRank.HIGH_CARD, bestCards);
    }

    // ==========================================================================================
    public static AnalysisResults analyzeHand(Player player) {
        AnalysisResults analysisResults = AnalysisEngine.checkForRoyalFlush(player);

        if (analysisResults.handRank() == HandRank.ROYAL_FLUSH)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForStraightFlush(player);
        if (analysisResults.handRank() == HandRank.STRAIGHT_FLUSH)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForFourOfAKind(player);
        if (analysisResults.handRank() == HandRank.FOUR_OF_A_KIND)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForFullHouse(player);
        if (analysisResults.handRank() == HandRank.FULL_HOUSE)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForFlush(player);
        if (analysisResults.handRank() == HandRank.FLUSH)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForStraight(player);
        if (analysisResults.handRank() == HandRank.STRAIGHT)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForThreeOfAKind(player);
        if (analysisResults.handRank() == HandRank.THREE_OF_A_KIND)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForTwoPair(player);
        if (analysisResults.handRank() == HandRank.TWO_PAIR)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForOnePair(player);
        if (analysisResults.handRank() == HandRank.ONE_PAIR)
            return analysisResults;

        analysisResults = AnalysisEngine.checkForHighCard(player);
        if (analysisResults.handRank() == HandRank.HIGH_CARD)
            return analysisResults;

        return analysisResults;
    }

    // ==========================================================================================
    private static Map<HandRank, List<String>> getHandRankToPlayerIdMap(List<Player> players) {
        Map<HandRank, List<String>> handRankToPlayerIdMap = new LinkedHashMap<>();

        for (HandRank handRank : HandRank.values()) {
            handRankToPlayerIdMap.put(handRank, new ArrayList<>());
        }

        for (Player player : players) {
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

    // ==========================================================================================
    // public static List<Winner> getWinners(List<Player> players) {
    // Map<HandRank, List<String>> handRankToPlayerIdMap = getHandRankToPlayerIdMap(players);
    // logger.info("handRankToPlayerIdMap: " + handRankToPlayerIdMap);

    // Map<HandRank, List<String>> reversedMap = new LinkedHashMap<>();

    // List<Map.Entry<HandRank, List<String>>> entryList =
    // new ArrayList<>(handRankToPlayerIdMap.entrySet());

    // for (int i = entryList.size() - 1; i >= 0; i--) {
    // Map.Entry<HandRank, List<String>> entry = entryList.get(i);
    // reversedMap.put(entry.getKey(), entry.getValue());
    // }

    // boolean winnersFound = false;
    // List<Winner> winners = new ArrayList<>();
    // for (Map.Entry<HandRank, List<String>> entry : reversedMap.entrySet()) {
    // if (!winnersFound && entry.getValue().size() > 0) {
    // winnersFound = true;
    // for (String id : entry.getValue()) {
    // winners.add(new Winner(id, entry.getKey(),
    // Game.getPlayers().get(id).getBestCards()));
    // }
    // }
    // }

    // return winners;
    // }

    // ==========================================================================================
    public List<Winner> getWinners(List<Player> players) {
        Map<HandRank, List<String>> handRankToPlayerIdMap = getHandRankToPlayerIdMap(players);
        logger.info("\nhandRankToPlayerIdMap: " + handRankToPlayerIdMap);

        if (logger.isInfoEnabled()) {
            for (Map.Entry<HandRank, List<String>> entry : handRankToPlayerIdMap.entrySet()) {
                logger.info(entry.getKey() + entry.getValue().toString());
            }

        }

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
                int index = 0;
                for (String id : entry.getValue()) {
                    List<Card> bestCards =
                            Game.getInstance().getPlayers().get(index).getBestCards();
                    Collections.sort(bestCards);
                    Winner winner = new Winner(id, entry.getKey(), bestCards);
                    winners.add(winner);
                }
            }
        }

        return winners;
    }
}
