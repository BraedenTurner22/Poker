package com.collegeshowdown.poker_project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.collegeshowdown.poker_project.model.Player;
import com.collegeshowdown.poker_project.model.Winner;
import com.collegeshowdown.poker_project.runtime.Lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lobby")
public class LobbyController {

    // Create a new instance of Lobby.
    // In a real application this might be a Spring-managed singleton.
    private Lobby lobby = new Lobby();

    /**
     * Get the current status of the lobby.
     * @return A JSON object containing the board and the list of players.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLobbyStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("board", lobby.getBoard());
        response.put("players", lobby.getPlayers());
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint for a player to join the lobby.
     * The client should send the Player object as JSON in the request body.
     * @param player A player entity containing the necessary details.
     * @return A confirmation message.
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, String>> joinLobby(@RequestBody Player player) {
        List<Player> players = lobby.getPlayers();
        if (players == null) {
            players = new ArrayList<>();
            lobby.setPlayers(players);
        }
        players.add(player);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Player " + player.getName() + " joined the lobby.");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to start the game.
     * This will call the play() method in your Lobby model.
     * @return A confirmation message.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startGame() {
        // Call the play method. Game-related logic would normally be processed here.
        lobby.play();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Game started.");
        return ResponseEntity.ok(response);
    }

    /**
     * Get the winners from the last game.
     * @return A list of Winner objects.
     */
    @GetMapping("/winners")
    public ResponseEntity<List<Winner>> getWinners() {
        List<Winner> winners = lobby.getWinners();
        return ResponseEntity.ok(winners);
    }
}
