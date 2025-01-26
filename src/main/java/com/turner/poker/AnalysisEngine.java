package com.turner.poker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalysisEngine {

    private final static Logger logger = LoggerFactory.getLogger(AnalysisEngine.class);

    public static HandRank checkForRoyalFlush(Player player) {
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

        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.info("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                logger.info("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCard = player.getAllCards().get(i).getRank().getValue();
                int nextCard = player.getAllCards().get(i + 1).getRank().getValue();
                logger.info("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.info("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCard == nextCard + 1) {
                    consecutiveCardCount++;
                }

                logger.info("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == player.getAllCards().size())
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            logger.info("returning HandRank.STRAIGHT");
            return HandRank.STRAIGHT;
        } else {
            logger.info("returning HandRank.NOTHING");
            return HandRank.NOTHING;
        }
    }

    public static HandRank checkForStraightFlush(Player player) {
        logger.info("player.getAllCards(): " + player.getAllCards());

        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.info("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                logger.info("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCard = player.getAllCards().get(i).getRank().getValue();
                int nextCard = player.getAllCards().get(i + 1).getRank().getValue();
                logger.info("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.info("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCard == nextCard + 1) {
                    consecutiveCardCount++;
                }

                logger.info("consecutiveCardCount: " + consecutiveCardCount);

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
                        logger.info("returning HandRank.STRAIGHT_FLUSH");
                        return HandRank.STRAIGHT_FLUSH;
                    }
                }
            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForFourOfAKind(Player player) {
        List<Card> bestCards = new ArrayList<>();
        logger.info("player.getAllCards().size(): " + player.getAllCards().size());
        logger.info("player.getAllCards(): " + player.getAllCards());
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.info("player.getAllCards().get(i).getRank(): "
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
                return HandRank.FOUR_OF_A_KIND;
            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForFullHouse(Player player) {
        List<Card> bestCards = new ArrayList<>();
        logger.info("player.getAllCards().size(): " + player.getAllCards().size());
        logger.info("player.getAllCards(): " + player.getAllCards());
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.info("player.getAllCards().get(i).getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 2)
                break;
            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1).getRank()
                    && player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 2)
                            .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                bestCards.add(player.getAllCards().get(i + 2));
                // player.setBestCards(bestCards);
                // logger.info("returning HandRank.THREE_OF_A_KIND");
                // return HandRank.THREE_OF_A_KIND;


                logger.info("player.getAllCards().size(): " + player.getAllCards().size());
                logger.info("player.getAllCards(): " + player.getAllCards());
                for (int j = 0; i < player.getAllCards().size(); j++) {
                    logger.info("player.getAllCards().get(" + i + ").getRank(): "
                            + player.getAllCards().get(j).getRank());

                    if (i == player.getAllCards().size() - 1)
                        break;

                    if (player.getAllCards().get(j).getRank() == player.getAllCards().get(j + 1)
                            .getRank()) {
                        bestCards.add(player.getAllCards().get(j));
                        bestCards.add(player.getAllCards().get(j + 1));
                        player.setBestCards(bestCards);
                        logger.info("returning HandRank.FULL_HOUSE");
                        return HandRank.FULL_HOUSE;
                    }
                }
                logger.info("returning HandRank.NOTHING");
                return HandRank.NOTHING;


            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;


    }

    public static HandRank checkForFlush(Player player) {
        for (Suit suit : Suit.values()) {
            int flushCount = 1;
            for (int i = 0; i < player.getAllCards().size(); i++) {
                if (player.getAllCards().get(i).getSuit() == suit) {
                    flushCount++;
                }
                if (flushCount == 5) {
                    logger.info("returning HandRank.FLUSH");
                    return HandRank.FLUSH;
                }
            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForStraight(Player player) {
        logger.info("player.getAllCards(): " + player.getAllCards());

        int consecutiveCardCount = 1;
        for (int pass = 0; pass < 3; pass++) {
            if (consecutiveCardCount == 5)
                break;
            consecutiveCardCount = 1;
            logger.info("pass: " + pass);
            for (int i = pass; i < pass + 5; i++) {
                logger.info("i: " + i);

                if (i == player.getAllCards().size() - 1)
                    break;

                int currentCard = player.getAllCards().get(i).getRank().getValue();
                int nextCard = player.getAllCards().get(i + 1).getRank().getValue();
                logger.info("currentCard: " + player.getAllCards().get(i).getRank().getValue());
                logger.info("nextCard: " + player.getAllCards().get(i + 1).getRank().getValue());

                if (currentCard == nextCard + 1) {
                    consecutiveCardCount++;
                }

                logger.info("consecutiveCardCount: " + consecutiveCardCount);

                if (consecutiveCardCount == 5 || i + 1 == player.getAllCards().size())
                    break;
            }
        }

        if (consecutiveCardCount == 5) {
            logger.info("returning HandRank.STRAIGHT");
            return HandRank.STRAIGHT;
        } else {
            logger.info("returning HandRank.NOTHING");
            return HandRank.NOTHING;
        }
    }

    public static HandRank checkForThreeOfAKind(Player player) {
        List<Card> bestCards = new ArrayList<>();
        logger.info("player.getAllCards().size(): " + player.getAllCards().size());
        logger.info("player.getAllCards(): " + player.getAllCards());
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.info("player.getAllCards().get(i).getRank(): "
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
                return HandRank.THREE_OF_A_KIND;
            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForTwoPair(Player player) {
        int pairs = 0;
        for (int i = 0; i < player.getAllCards().size(); i++) {
            if (i == player.getAllCards().size() - 1)
                break;
            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1)
                    .getRank()) {
                pairs++;
            }
        }

        if (pairs == 2) {
            logger.info("returning HandRank.TWO_PAIR");
            return HandRank.TWO_PAIR;
        }

        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForOnePair(Player player) {
        List<Card> bestCards = new ArrayList<>();
        logger.info("player.getAllCards().size(): " + player.getAllCards().size());
        logger.info("player.getAllCards(): " + player.getAllCards());
        for (int i = 0; i < player.getAllCards().size(); i++) {
            logger.info("player.getAllCards().get(" + i + ").getRank(): "
                    + player.getAllCards().get(i).getRank());

            if (i == player.getAllCards().size() - 1)
                break;

            if (player.getAllCards().get(i).getRank() == player.getAllCards().get(i + 1)
                    .getRank()) {
                bestCards.add(player.getAllCards().get(i));
                bestCards.add(player.getAllCards().get(i + 1));
                player.setBestCards(bestCards);
                logger.info("returning HandRank.ONE_PAIR");
                return HandRank.ONE_PAIR;
            }
        }
        logger.info("returning HandRank.NOTHING");
        return HandRank.NOTHING;
    }

    public static HandRank checkForHighCard(Player player) {
        Card highCard = player.getAllCards().get(player.getAllCards().size() - 1);
        List<Card> bestCards = new ArrayList<>();
        bestCards.add(highCard);
        player.setBestCards(bestCards);
        return HandRank.HIGH_CARD;
    }

    public static void setHandRank(Player player) {
        HandRank handRank = AnalysisEngine.checkForRoyalFlush(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForStraightFlush(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForFourOfAKind(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForFullHouse(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForFlush(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForStraight(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForThreeOfAKind(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForTwoPair(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForOnePair(player);
        if (handRank == HandRank.NOTHING)
            handRank = AnalysisEngine.checkForHighCard(player);
        player.setHandRank(handRank);
    }

    public static Map<HandRank, List<String>> getHandRankToPlayerIdMap() {
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
