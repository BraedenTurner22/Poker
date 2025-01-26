package com.turner.poker;

import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ForcedCardsTestDriver {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(ForcedCardsTestDriver.class);

    public static void main(String[] args) {
        try {
            for (int i = 1; i <= Integer.parseInt(args[0]); i++) {
                String path = "src/main/resources/player-" + i + "-cards.json";
                Player player = objectMapper.readValue(new File(path), Player.class);
                Players.addPlayer(player);
            }

            List<Card> boardCards =
                    objectMapper.readValue(new File("src/main/resources/board-cards.json"),
                            new TypeReference<List<Card>>() {});
            Board.layoutCards(boardCards);
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }

        logger.info("---Board---");
        logger.info(Board.staticToString());

        logger.info("---Players---");
        logger.info(Players.staticToString());

        List<Winner> winners = Game.getWinners();
        logger.info("---Winner---");
        logger.info("winners: " + winners);
    }
}
