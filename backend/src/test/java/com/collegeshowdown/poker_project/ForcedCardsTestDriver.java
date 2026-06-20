package com.collegeshowdown.poker_project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual test harness for replaying hands from JSON fixture files.
 * TODO: wire up once WebSocket + lobby creation endpoints exist.
 */
public class ForcedCardsTestDriver {

    private final static Logger logger = LoggerFactory.getLogger(ForcedCardsTestDriver.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: ForcedCardsTestDriver <game-file>");
            System.exit(1);
        }

        String file = "src/test/resources/" + args[0];
        logger.info("Loading fixture: {}", file);
        // TODO: deserialize Lobby from JSON, inject services, call lobbyGameService.startHand()
    }
}
