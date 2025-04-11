package com.collegeshowdown.poker_project.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.collegeshowdown.poker_project.model.Game;
import com.collegeshowdown.poker_project.model.Lobby;
import com.collegeshowdown.poker_project.model.Player;
import com.collegeshowdown.poker_project.repositories.GameRepository;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlayerRepository playerRepository;

    // Store active games in memory (for development/testing)
    private Map<String, Game> activeGames = new HashMap<>();

    @PostConstruct
    public void init() {
        // Initialize with a default game for testing
        Game defaultGame = new Game();
        defaultGame.setName("Default Game");
        activeGames.put("default", defaultGame);
    }

    /**
     * Get a game by ID
     * @param gameId The game ID
     * @return The game if found, null otherwise
     */
    public Game getGame(String gameId) {
        // First check in-memory games
        if (activeGames.containsKey(gameId)) {
            return activeGames.get(gameId);
        }

        // Then check database
        Optional<Game> game = gameRepository.findById(Integer.parseInt(gameId));
        return game.orElse(null);
    }

    /**
     * Create a new game
     * @param name The game name
     * @return The created game
     */
    public Game createGame(String name) {
        Game game = new Game();
        game.setName(name);

        // Save to database
        game = gameRepository.save(game);

        // Add to active games
        activeGames.put(String.valueOf(game.getId()), game);

        return game;
    }

    /**
     * Add a player to a game
     * @param gameId The game ID
     * @param player The player to add
     * @return The updated game
     */
    public Game addPlayer(String gameId, Player player) {
        Game game = getGame(gameId);
        if (game == null) {
            return null;
        }

        // Save player first
        player = playerRepository.save(player);

        // Add to game
        List<Player> players = game.getPlayers();
        if (players == null) {
            players = new ArrayList<>();
            game.setPlayers(players);
        }
        players.add(player);

        // Update game in database
        game = gameRepository.save(game);

        // Update in active games
        activeGames.put(gameId, game);

        return game;
    }

    /**
     * Start a game
     * @param gameId The game ID
     * @return The updated game
     */
    public Game startGame(String gameId) {
        Game game = getGame(gameId);
        if (game == null) {
            return null;
        }

        // Start the game
        game.play();

        // Save changes to database
        game = gameRepository.save(game);

        // Update in active games
        activeGames.put(gameId, game);

        return game;
    }

    /**
     * Process a bet in a game
     * @param gameId The game ID
     * @param username The player's username
     * @param betAmount The bet amount
     * @return Result message
     */
    public String processBet(String gameId, String username, Double betAmount) {
        Game game = getGame(gameId);
        if (game == null) {
            return "Game not found";
        }

        // Find player
        Optional<Player> playerOpt = game.getPlayers().stream()
                .filter(p -> p.getName().equals(username))
                .findFirst();

        if (playerOpt.isEmpty()) {
            return "Player not found";
        }

        Player player = playerOpt.get();

        // Process the bet
        String result = game.processBet(player.getId(), betAmount);

        // Update game in database
        gameRepository.save(game);

        return result;
    }

    /**
     * Process a fold action
     * @param gameId The game ID
     * @param username The player's username
     * @return Result message
     */
    public String processFold(String gameId, String username) {
        // Implementation for folding
        return "Player " + username + " folded";
    }

    /**
     * Process a check action
     * @param gameId The game ID
     * @param username The player's username
     * @return Result message
     */
    public String processCheck(String gameId, String username) {
        // Implementation for checking
        return "Player " + username + " checked";
    }

    /**
     * Process a call action
     * @param gameId The game ID
     * @param username The player's username
     * @return Result message
     */
    public String processCall(String gameId, String username) {
        // Implementation for calling
        return "Player " + username + " called";
    }
}