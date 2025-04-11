package com.collegeshowdown.poker_project.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.collegeshowdown.poker_project.runtime.Card;

public class AnalysisEngine {
    private final static Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    // ==========================================================================================
    private static AnalysisResults checkForRoyalFlush(Player player) {
        logger.debug("id: " + player.getId());
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        Map<Suit, List<Card>> suitToCardMap = createSuitToCardMap(player);
        for (Suit suit : suitToCardMap.keySet()) {
            List<Card> cards = suitToCardMap.get(suit);
            if (cards.size() < 5)
                continue;

            if (containsAce(cards) && containsKing(cards) && containsQueen(cards)
                    && containsJack(cards) && containsTen(cards)) {

                player.setBestCards(cards);
                player.setHandRank(HandRank.ROYAL_FLUSH);
                logger.info("return HandRank.ROYAL_FLUSH");
                return new AnalysisResults(player.getId(), HandRank.ROYAL_FLUSH,
                        player.getBestCards());
            }
        }

        // for (int pass = 0; pass < 3; pass++) {
        // int sum = (cards.get(pass).getRank().getValue()
        // * cards.get(pass).getSuit().getValue())
        // + (cards.get(pass + 1).getRank().getValue()
        // * cards.get(pass + 1).getSuit().getValue())
        // + (cards.get(pass + 2).getRank().getValue()
        // * cards.get(pass + 2).getSuit().getValue())
        // + (cards.get(pass + 3).getRank().getValue()
        // * cards.get(pass + 3).getSuit().getValue())
        // + (cards.get(pass + 4).getRank().getValue()
        // * cards.get(pass + 4).getSuit().getValue());

        // logger.debug("sum: " + sum);
        // if (sum % 60 == 0) {
        // List<Card> bestCards = player.getCards();
        // for (int j = pass; j < 5; j++) {
        // bestCards.add(cards.get(j));
        // }

        // logger.info("return HandRank.ROYAL_FLUSH");
        // return new AnalysisResults(player.getId(), HandRank.ROYAL_FLUSH, bestCards);
        // }
        // }

        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);

    }

    // ==========================================================================================
    private static AnalysisResults checkForStraightFlush(Player player) {
        logger.debug("id: " + player.getId());
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        Map<Suit, List<Card>> suitToCardMap = createSuitToCardMap(player);
        for (Suit suit : suitToCardMap.keySet()) {
            List<Card> cards = suitToCardMap.get(suit);
            if (cards.size() < 5)
                continue;

            if ((containsKing(cards) && containsQueen(cards) && containsJack(cards)
                    && containsTen(cards) && containsNine(cards))
                    || (containsQueen(cards) && containsJack(cards) && containsTen(cards)
                            && containsNine(cards) && containsEight(cards))
                    || (containsJack(cards) && containsTen(cards) && containsNine(cards)
                            && containsEight(cards) && containsSeven(cards))
                    || (containsTen(cards) && containsNine(cards) && containsEight(cards)
                            && containsSeven(cards) && containsSix(cards))
                    || (containsNine(cards) && containsEight(cards) && containsSeven(cards)
                            && containsSix(cards) && containsFive(cards))
                    || (containsEight(cards) && containsSeven(cards) && containsSix(cards)
                            && containsFive(cards) && containsFour(cards))
                    || (containsSeven(cards) && containsSix(cards) && containsFive(cards)
                            && containsFour(cards) && containsThree(cards))
                    || (containsSix(cards) && containsFive(cards) && containsFour(cards)
                            && containsThree(cards) && containsTwo(cards))
                    || (containsFive(cards) && containsFour(cards) && containsThree(cards)
                            && containsTwo(cards)) && containsAce(cards)) {

                player.setHandRank(HandRank.STRAIGHT_FLUSH);
                logger.info("return HandRank.STRAIGHT_FLUSH");
                return new AnalysisResults(player.getId(), HandRank.STRAIGHT_FLUSH,
                        player.getBestCards());
            }
        }

        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);

    }

    // ==========================================================================================
    private static AnalysisResults checkForFourOfAKind(Player player) {
        List<Card> playerCards = new ArrayList<>(player.getCards());
        int size = playerCards.size();
        logger.debug("id: " + player.getId());
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("rank: " + playerCards.get(i).getRank());

            if (i == player.getCards().size() - 3)
                break;

            if (playerCards.get(i).getRank() == playerCards.get(i + 1).getRank()
                    && playerCards.get(i).getRank() == playerCards.get(i + 2).getRank()
                    && playerCards.get(i).getRank() == playerCards.get(i + 3).getRank()) {
                // Adds 4 cards that are FOAK and removes them
                for (int j = 0; j < 4; j++) {
                    bestCards.add(playerCards.get(i));
                    playerCards.remove(i);
                }

                // Adds highest card that isn't part of FOAK
                bestCards.add(playerCards.get(0));

                player.setBestCards(bestCards);
                player.setHandRank(HandRank.FOUR_OF_A_KIND);
                logger.info("return HandRank.FOUR_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.FOUR_OF_A_KIND, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    private static AnalysisResults checkForFullHouse(Player player) {
        List<Card> playerCards = new ArrayList<>(player.getCards());
        Map<Rank, Integer> rankCount = new HashMap<>();

        // Count occurrences of each rank
        for (Card card : playerCards) {
            rankCount.put(card.getRank(), rankCount.getOrDefault(card.getRank(), 0) + 1);
        }

        Rank threeOfAKindRank = null;
        Rank pairRank = null;

        // Find the best three-of-a-kind and the best pair
        for (Map.Entry<Rank, Integer> entry : rankCount.entrySet()) {
            int count = entry.getValue();
            Rank rank = entry.getKey();

            if (count >= 3) {
                if (threeOfAKindRank == null || rank.getValue() > threeOfAKindRank.getValue()) {
                    threeOfAKindRank = rank;
                }
            } else if (count == 2) {
                if (pairRank == null || rank.getValue() > pairRank.getValue()) {
                    pairRank = rank;
                }
            }
        }

        // Check if we have a valid full house
        if (threeOfAKindRank != null && pairRank != null) {
            List<Card> bestCards = new ArrayList<>();

            // Add Three of a Kind cards
            for (Card card : playerCards) {
                if (card.getRank() == threeOfAKindRank) {
                    bestCards.add(card);
                }
            }

            // Add Pair cards
            for (Card card : playerCards) {
                if (card.getRank() == pairRank) {
                    bestCards.add(card);
                }
            }

            player.setBestCards(bestCards);
            player.setHandRank(HandRank.FULL_HOUSE);
            logger.info("return HANDRANK.FULL_HOUSE (" + pairRank + " full of " + threeOfAKindRank
                    + ")");
            return new AnalysisResults(player.getId(), HandRank.FULL_HOUSE, bestCards);
        }

        logger.info("return HANDRANK.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    private static AnalysisResults checkForFlush(Player player) {
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
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
                    player.setHandRank(HandRank.FLUSH);
                    logger.info("return HandRank.FLUSH");
                    return new AnalysisResults(player.getId(), HandRank.FLUSH, bestCards);
                }
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    private static AnalysisResults checkForStraight(Player player) {
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
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
            player.setHandRank(HandRank.STRAIGHT);
            logger.info("return HandRank.STRAIGHT");
            return new AnalysisResults(player.getId(), HandRank.STRAIGHT, bestCards);
        } else {
            logger.info("return HandRank.NOTHING");
            return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
        }
    }

    // ==========================================================================================
    private static AnalysisResults checkForThreeOfAKind(Player player) {
        List<Card> playerCards = new ArrayList<>(player.getCards());
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            logger.debug("rank: " + player.getCards().get(i).getRank());

            if (i == size - 2)
                break;

            if (playerCards.get(i).getRank() == playerCards.get(i + 1).getRank()
                    && playerCards.get(i).getRank() == playerCards.get(i + 2).getRank()) {
                for (int j = 0; j < 3; j++) {
                    bestCards.add(playerCards.get(i));
                    playerCards.remove(i);
                }

                // Adds highest cards that aren't TOAK
                bestCards.add(playerCards.get(0));
                bestCards.add(playerCards.get(1));

                player.setBestCards(bestCards);
                player.setHandRank(HandRank.THREE_OF_A_KIND);
                logger.info("return HandRank.THREE_OF_A_KIND");
                return new AnalysisResults(player.getId(), HandRank.THREE_OF_A_KIND, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    private static AnalysisResults checkForTwoPair(Player player) {
        List<Card> playerCards = new ArrayList<>(player.getCards());
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        int pairCount = 0;
        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size - 1; i++) {

            if (i < playerCards.size() - 1
                    && playerCards.get(i).getRank() == playerCards.get(i + 1).getRank()) {
                pairCount++;
                bestCards.add(playerCards.get(i));
                bestCards.add(playerCards.get(i + 1));
                // Remove these cards from consideration for the next pair
                playerCards.remove(i);
                playerCards.remove(i);
                i--;
            }
        }

        if (pairCount == 2 && !playerCards.isEmpty()) {
            bestCards.add(playerCards.get(0));
            player.setBestCards(bestCards);
            player.setHandRank(HandRank.TWO_PAIR);
            logger.info("return HandRank.TWO_PAIR");
            return new AnalysisResults(player.getId(), HandRank.TWO_PAIR, bestCards);

        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }


    // ==========================================================================================
    private static AnalysisResults checkForOnePair(Player player) {
        List<Card> playerCards = new ArrayList<>(player.getCards());
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
        logger.debug("size: " + player.getCards().size());
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();
        for (int i = 0; i < size - 1; i++) {
            logger.debug("cards[" + i + "].rank: " + playerCards.get(i).getRank());

            if (playerCards.get(i).getRank() == playerCards.get(i + 1).getRank()) {
                bestCards.add(playerCards.get(i));
                bestCards.add(playerCards.get(i + 1));
                // Remove these cards from consideration for the next pair
                playerCards.remove(i);
                playerCards.remove(i);

                // Add three best kickers
                for (int j = 0; j < 3; j++) {
                    bestCards.add(playerCards.get(0));
                    playerCards.remove(0);
                }

                player.setBestCards(bestCards);
                player.setHandRank(HandRank.ONE_PAIR);
                logger.info("return HandRank.ONE_PAIR");
                return new AnalysisResults(player.getId(), HandRank.ONE_PAIR, bestCards);
            }
        }
        logger.info("return HandRank.NOTHING");
        return new AnalysisResults(player.getId(), HandRank.NOTHING, null);
    }

    // ==========================================================================================
    private static AnalysisResults checkForHighCard(Player player) {
        int size = player.getCards().size();
        logger.debug("id: " + player.getId());
        logger.debug("size: " + size);
        logger.debug("cards: " + player.getCards());

        List<Card> bestCards = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            bestCards.add(player.getCards().get(i));
        }
        player.setBestCards(bestCards);
        player.setHandRank(HandRank.HIGH_CARD);
        logger.info("return HandRank.HIGH_CARD");
        return new AnalysisResults(player.getId(), HandRank.HIGH_CARD, bestCards);
    }

    // ==========================================================================================
    public static AnalysisResults analyzeHand(Player player) {
        AnalysisResults analysisResults = checkForRoyalFlush(player);

        if (analysisResults.handRank() == HandRank.ROYAL_FLUSH)
            return analysisResults;

        analysisResults = checkForStraightFlush(player);
        if (analysisResults.handRank() == HandRank.STRAIGHT_FLUSH)
            return analysisResults;

        analysisResults = checkForFourOfAKind(player);
        if (analysisResults.handRank() == HandRank.FOUR_OF_A_KIND)
            return analysisResults;

        analysisResults = checkForFullHouse(player);
        if (analysisResults.handRank() == HandRank.FULL_HOUSE)
            return analysisResults;

        analysisResults = checkForFlush(player);
        if (analysisResults.handRank() == HandRank.FLUSH)
            return analysisResults;

        analysisResults = checkForStraight(player);
        if (analysisResults.handRank() == HandRank.STRAIGHT)
            return analysisResults;

        analysisResults = checkForThreeOfAKind(player);
        if (analysisResults.handRank() == HandRank.THREE_OF_A_KIND)
            return analysisResults;

        analysisResults = checkForTwoPair(player);
        if (analysisResults.handRank() == HandRank.TWO_PAIR)
            return analysisResults;

        analysisResults = checkForOnePair(player);
        if (analysisResults.handRank() == HandRank.ONE_PAIR)
            return analysisResults;

        analysisResults = checkForHighCard(player);
        if (analysisResults.handRank() == HandRank.HIGH_CARD)
            return analysisResults;

        return analysisResults;
    }

    // ==========================================================================================
    private static Map<HandRank, List<String>> getHandRankToPlayerIdMap(List<Player> players) {
        Map<HandRank, List<Integer>> handRankToPlayerIdMap = new LinkedHashMap<>();

        for (HandRank handRank : HandRank.values())
            handRankToPlayerIdMap.put(handRank, new ArrayList<>());

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
    private static List<Winner> getRoyalFlushWinner(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.ROYAL_FLUSH) {
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
                return winners;
            }
        }
        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getStraightFlushWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.STRAIGHT_FLUSH)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        // If the board is the straight flush, then returns everyone with a straight flush

        // Determines winner if 2 people have straight flush based on higher last card
        if (winners.size() == 2) {
            Winner w1 = winners.get(0);
            Winner w2 = winners.get(1);

            int highCard1 = w1.getWinningCardAtIndex(0).getRank().getValue();
            int highCard2 = w2.getWinningCardAtIndex(0).getRank().getValue();

            if (highCard1 > highCard2) {
                winners.remove(1); // Remove w2
            } else if (highCard1 < highCard2) {
                winners.remove(0); // Remove w1
            }
        }
        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getFourOfAKindWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.FOUR_OF_A_KIND)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        // Find winner between two winners
        if (winners.size() == 2) {
            Winner w1 = winners.get(0);
            Winner w2 = winners.get(1);

            int w1Size = w1.getWinningCards().size();
            int w2Size = w2.getWinningCards().size();

            int quad1 = w1.getWinningCardAtIndex(0).getRank().getValue();
            int quad2 = w2.getWinningCardAtIndex(0).getRank().getValue();
            int kicker1 = w1.getWinningCardAtIndex(w1Size - 1).getRank().getValue();
            int kicker2 = w2.getWinningCardAtIndex(w2Size - 1).getRank().getValue();

            if (quad1 > quad2 || (quad1 == quad2 && kicker1 > kicker2)) {
                winners.remove(1);
            } else {
                winners.remove(0);
            }
        }

        // Find the winner among >2 players (highest kicker)
        else if (winners.size() > 2) {
            int highestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestKicker);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getFullHouseWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.FULL_HOUSE)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }


        // TODO: Figure out if this is right
        if (winners.size() > 1) {
            int highestTOAK = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestTOAK);

            int highestPair = winners.stream().mapToInt(w -> w
                    .getWinningCardAtIndex(w.getWinningCards().size() - 1).getRank().getValue())
                    .max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(w.getWinningCards().size() - 1).getRank()
                    .getValue() < highestPair);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getFlushWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.FLUSH)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            for (int i = 0; i < 5; i++) {
                final int index = i;
                int highestFlushCard = winners.stream()
                        .mapToInt(w -> w.getWinningCardAtIndex(index).getRank().getValue()).max()
                        .orElse(0);

                winners.removeIf(w -> w.getWinningCardAtIndex(index).getRank()
                        .getValue() < highestFlushCard);
            }
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getStraightWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.STRAIGHT)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            int highestFlushCard = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestFlushCard);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getThreeOfAKindWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.THREE_OF_A_KIND)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            int highestTOAK = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestTOAK);

            int highestKicker = winners.stream().mapToInt(w -> w
                    .getWinningCardAtIndex(w.getWinningCards().size() - 2).getRank().getValue())
                    .max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(w.getWinningCards().size() - 2).getRank()
                    .getValue() < highestKicker);

            int secondHighestKicker = winners.stream().mapToInt(w -> w
                    .getWinningCardAtIndex(w.getWinningCards().size() - 1).getRank().getValue())
                    .max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(w.getWinningCards().size() - 1).getRank()
                    .getValue() < secondHighestKicker);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getTwoPairWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.TWO_PAIR)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            int highestPair = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestPair);

            int secondHighestPair = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(2).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(2).getRank().getValue() < secondHighestPair);

            int highestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(4).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(4).getRank().getValue() < highestKicker);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getOnePairWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        for (Player player : players) {
            if (player.getHandRank() == HandRank.ONE_PAIR)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            int highestPair = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestPair);

            int highestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(2).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(2).getRank().getValue() < highestKicker);

            int secondHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(3).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(3).getRank().getValue() < secondHighestKicker);

            int thirdHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(4).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(4).getRank().getValue() < thirdHighestKicker);
        }

        return winners;
    }

    // ==========================================================================================
    private static List<Winner> getHighCardWinners(List<Player> players) {
        List<Winner> winners = new ArrayList<>();
        Rank rank = Rank.TWO;

        for (Player player : players) {
            if (player.getHandRank() == HandRank.HIGH_CARD)
                winners.add(
                        new Winner(player.getId(), player.getHandRank(), player.getBestCards()));
        }

        if (winners.size() > 1) {
            int highestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(0).getRank().getValue()).max().orElse(0);

            winners.removeIf(w -> w.getWinningCardAtIndex(0).getRank().getValue() < highestKicker);

            int secondHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(1).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(1).getRank().getValue() < secondHighestKicker);

            int thirdHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(2).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(2).getRank().getValue() < thirdHighestKicker);

            int fourthHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(3).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(3).getRank().getValue() < fourthHighestKicker);

            int fifthHighestKicker = winners.stream()
                    .mapToInt(w -> w.getWinningCardAtIndex(4).getRank().getValue()).max().orElse(0);

            winners.removeIf(
                    w -> w.getWinningCardAtIndex(4).getRank().getValue() < fifthHighestKicker);
        }

        return winners;
    }

    // ==========================================================================================
    public static List<Winner> getWinners(List<Player> players) {

        Map<HandRank, List<String>> handRankToPlayerIdMap = getHandRankToPlayerIdMap(players);
        logger.info("\nhandRankToPlayerIdMap: " + handRankToPlayerIdMap);

        List<Winner> winners = new ArrayList<>();

        winners = getRoyalFlushWinner(players);
        if (winners.size() > 0)
            return winners;
        winners = getStraightFlushWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getFourOfAKindWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getFullHouseWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getFlushWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getStraightWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getThreeOfAKindWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getTwoPairWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getOnePairWinners(players);
        if (winners.size() > 0)
            return winners;
        winners = getHighCardWinners(players);
        if (winners.size() > 0)
            return winners;

        return winners;


        // Map<HandRank, List<String>> handRankToPlayerIdMap = getHandRankToPlayerIdMap(players);
        // logger.info("\nhandRankToPlayerIdMap: " + handRankToPlayerIdMap);

        // if (logger.isInfoEnabled()) {
        // for (Map.Entry<HandRank, List<String>> entry : handRankToPlayerIdMap.entrySet())
        // logger.info(entry.getKey() + entry.getValue().toString());
        // }

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
        // int index = 0;
        // for (String id : entry.getValue()) {
        // List<Card> bestCards =
        // Game.getInstance().getPlayers().get(index).getBestCards();
        // Collections.sort(bestCards);
        // Winner winner = new Winner(id, entry.getKey(), bestCards);
        // winners.add(winner);
        // }
        // }
        // }

        // return winners;
    }

    // ==========================================================================================
    private static boolean containsAce(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.ACE)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsKing(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.KING)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsQueen(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.QUEEN)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsJack(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.JACK)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsTen(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.TEN)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsNine(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.NINE)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsEight(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.EIGHT)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsSeven(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.SEVEN)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsSix(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.SIX)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsFive(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.FIVE)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsFour(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.FOUR)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsThree(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.THREE)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static boolean containsTwo(List<Card> cards) {
        for (Card card : cards) {
            if (card.getRank() == Rank.TWO)
                return true;
        }
        return false;
    }

    // ==========================================================================================
    private static Map<Suit, List<Card>> createSuitToCardMap(Player player) {
        Map<Suit, List<Card>> suitToCardListMap = new HashMap<>();

        for (Suit suit : Suit.values()) {
            suitToCardListMap.put(suit, new ArrayList<>());
        }

        for (Card card : player.getCards()) {
            Suit suit = card.getSuit();
            List<Card> list = suitToCardListMap.get(suit);
            list.add(card);
            suitToCardListMap.put(suit, list);
        }
        return suitToCardListMap;
    }
}
