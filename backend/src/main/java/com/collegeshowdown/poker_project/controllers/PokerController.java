package com.example.pokergame.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.collegeshowdown.poker_project.model.Player;
import com.collegeshowdown.poker_project.repository.PlayerRepository;

@RestController
@RequestMapping("/api")
public class PokerController {

    @Autowired
    private GameService gameService;

    @PostMapping("/bet")
    public ResponseEntity<Map<String, String>> placeBet(@RequestBody Map<String, Object> payload) {
        String gameId = (String) payload.get("gameId");
        String username = (String) payload.get("username");
        Double betAmount = Double.parseDouble(payload.get("betAmount").toString());
        
        String result = gameService.processBet(gameId, username, betAmount);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        return ResponseEntity.ok(response);
    }
    
    // ... other in-game endpoints
}


