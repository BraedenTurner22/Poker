package com.collegeshowdown.poker_project.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.collegeshowdown.poker_project.model.Game;
import com.collegeshowdown.poker_project.model.Player;
import com.collegeshowdown.poker_project.model.Winner;
import com.collegeshowdown.poker_project.services.GameService;
import com.collegeshowdown.poker_project.repositories.PlayerRepository;

@RestController
@RequestMapping("/api/poker")
@CrossOrigin(origins = "*") // Enable CORS for development
public class PokerController {
    @Autowired
    private GameService gameService;

    @Autowired
    private PlayerRepository playerRepository;

    /**
     * Get all active games
     * @return List of games
     */
    @GetMapping("/games")
    public ResponseEntity<List<Game>> getAllGames() {
        return ResponseEntity.ok(gameService.getAllGames());
    }

    /**
     * Create a new game
     * @param payload The game details
     * @return The created game
     */
    @PostMapping("/games")
    public ResponseEntity<Game> createGame(@RequestBody Map<String, String> payload) {
        String gameName = payload.get("name");
        Game game = gameService.createGame(gameName);
        return ResponseEntity.status(HttpStatus.CREATED).body(game);
    }

    /**
     * Get a specific game by ID
     * @param gameId The game ID
     * @return The game
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable String gameId) {
        Game game = gameService.getGame(gameId);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(game);
    }

    /**
     * Add a player to a game
     * @param gameId The game ID
     * @param player The player to add
     * @return The updated game
     */
    @PostMapping("/games/{gameId}/players")
    public ResponseEntity<Game> addPlayer(@PathVariable String gameId, @RequestBody Player player) {
        Game game = gameService.addPlayer(gameId, player);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(game);
    }

    /**
     * Start a game
     * @param gameId The game ID
     * @return The updated game
     */
    @PostMapping("/games/{gameId}/start")
    public ResponseEntity<Game> startGame(@PathVariable String gameId) {
        Game game = gameService.startGame(gameId);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(game);
    }

    /**
     * Place a bet
     * @param gameId The game ID
     * @param payload The bet details
     * @return Result message
     */
    @PostMapping("/games/{gameId}/bet")
    public ResponseEntity<Map<String, String>> placeBet(
            @PathVariable String gameId,
            @RequestBody Map<String, Object> payload) {

        String username = (String) payload.get("username");
        Double betAmount = Double.parseDouble(payload.get("betAmount").toString());

        String result = gameService.processBet(gameId, username, betAmount);

        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        return ResponseEntity.ok(response);
    }

    /**
     * Fold
     * @param gameId The game ID
     * @param payload The action details
     * @return Result message
     */
    @PostMapping("/games/{gameId}/fold")
    public ResponseEntity<Map<String, String>> fold(
            @PathVariable String gameId,
            @RequestBody Map<String, Object> payload) {

        String username = (String) payload.get("username");

        String result = gameService.processFold(gameId, username);

        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        return ResponseEntity.ok(response);
    }

    /**
     * Check
     * @param gameId The game ID
     * @param payload The action details
     * @return Result message
     */
    @PostMapping("/games/{gameId}/check")
    public ResponseEntity<Map<String, String>> check(
            @PathVariable String gameId,
            @RequestBody Map<String, Object> payload) {

        String username = (String) payload.get("username");

        String result = gameService.processCheck(gameId, username);

        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        return ResponseEntity.ok(response);
    }

    /**
     * Call
     * @param gameId The game ID
     * @param payload The action details
     * @return Result message
     */
    @PostMapping("/games/{gameId}/call")
    public ResponseEntity<Map<String, String>> call(
            @PathVariable String gameId,
            @RequestBody Map<String, Object> payload) {

        String username = (String) payload.get("username");

        String result = gameService.processCall(gameId, username);

        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        return ResponseEntity.ok(response);
    }

    /**
     * Get the winners of a game
     * @param gameId The game ID
     * @return The winners
     */
    @GetMapping("/games/{gameId}/winners")
    public ResponseEntity<List<Winner>> getWinners(@PathVariable String gameId) {
        Game game = gameService.getGame(gameId);
        if (game == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(game.getWinners());
    }

    /**
     * Get all players
     * @return List of players
     */
    @GetMapping("/players")
    public ResponseEntity<List<Player>> getAllPlayers() {
        return ResponseEntity.ok(playerRepository.findAll());
    }

    /**
     * Get a player by ID
     * @param playerId The player ID
     * @return The player
     */
    @GetMapping("/players/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable int playerId) {
        Optional<Player> player = playerRepository.findById(playerId);
        return player.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create or update a player
     * @param player The player details
     * @return The created/updated player
     */
    @PostMapping("/players")
    public ResponseEntity<Player> createPlayer(@RequestBody Player player) {
        Player savedPlayer = playerRepository.save(player);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPlayer);
    }
}