package com.turner.poker;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ForcedCardsTestDriver {

    private final static Logger logger = LoggerFactory.getLogger(ForcedCardsTestDriver.class);

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();

        if (args.length != 1) {
            System.out.println("ERROR: usage: ForcedCardsTestDriver game-file ");
            System.exit(1);
        }

        try {
            String file = "test/main/resources/" + args[0];
            Game game = objectMapper.readValue(new File(file), Game.class);

            logger.info("game: " + game);

            game.play();

            logger.info("Winner: " + game.getWinners());

            // Print the parsed Game object
        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }

    }
}
