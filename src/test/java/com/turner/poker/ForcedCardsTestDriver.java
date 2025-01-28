package com.turner.poker;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForcedCardsTestDriver {

    private final static Logger logger = LoggerFactory.getLogger(ForcedCardsTestDriver.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("ERROR: usage: ForcedCardsTestDriver game-file ");
            System.exit(1);
        }


        try {
            // ObjectMapper objectMapper = new ObjectMapper();
            // objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            // // for (int i = 1; i <= Integer.parseInt(args[1]); i++) {
            // // String playerCardFile = "src/main/resources/player-" + i + "-cards.json";
            // // Player player = objectMapper.readValue(new File(playerCardFile), Player.class);
            // // Players.addPlayer(player);
            // // }

            // // String boardCardFile = "src/main/resources/" + args[0];
            // // List<Card> boardCards = objectMapper.readValue(new File(boardCardFile),
            // // new TypeReference<List<Card>>() {});
            // // Board.layoutCards(boardCards);


            // String gameFile = "src/main/resources/" + args[0];
            // // JsonNode rootNode = objectMapper.readTree(new File(gameFile));

            // // Access fields
            // List<Card> board =
            // objectMapper.readValue(new File(gameFile), new TypeReference<List<Card>>() {});

            // logger.info("board: " + board);

            // // List<Player> players = objectMapper.readValue(new File(gameFile),
            // // new TypeReference<List<Player>>() {});

            // // logger.info("players: " + players);


            // String content =
            // new String(Files.readAllBytes(Paths.get("src/main/resources/data.json")));
            // JSONObject jsonObject = new JSONObject(content);

            // // Access data from the JSON
            // String name = jsonObject.getString("name");
            // int age = jsonObject.getInt("age");

            // logger.info("name: " + name, ", " + age);


            // String content =
            // new String(Files.readAllBytes(Paths.get("src/main/resources/data.json")));
            // JSONObject jsonObject = new JSONObject(content);

            // // Access data from the JSON
            // List<Card> name = jsonObject.getList("board");
            // int age = jsonObject.getInt("age");

            // logger.info("name: " + name, ", " + age);

            // System.exit(1);


            // Read JSON file content into a String
            String content =
                    new String(Files.readAllBytes(Paths.get("src/main/resources/data.json")));

            // Parse the content into a JSONObject
            JSONObject jsonObject = new JSONObject(content);

            // Access simple fields
            String name = jsonObject.getString("name");

            JSONArray boardCardsArray = jsonObject.getJSONArray("cards");
            List<String> cards = new ArrayList<>();
            for (int i = 0; i < boardCardsArray.length(); i++) {
                cards.add(boardCardsArray.getString(i));
            }

            // // Access the nested address object
            // JSONObject addressObject = jsonObject.getJSONObject("address");
            // String street = addressObject.getString("street");
            // String city = addressObject.getString("city");
            // String zip = addressObject.getString("zip");

            // Access the friends array of objects
            JSONArray playersArray = jsonObject.getJSONArray("players");
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < playersArray.length(); i++) {
                JSONObject playerObject = playersArray.getJSONObject(i);
                String id = playerObject.getString("id");
                JSONArray playerCardsArray = playerObject.getJSONArray("cards");
                for (int j = 0; j < playerCardsArray.length(); i++) {
                    JSONObject playerCardObject = playerCardsArray.getJSONObject(i);
                    // Card card = playerCardObject.getJSONObject(j);
                    // players.add(new Player(id, playerCardsArray.getJSONObject()));
            }




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
