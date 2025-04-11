package com.collegeshowdown.poker_project.controllers;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketsController {

    // When a client sends a message to /app/game-update, broadcast it to /topic/updates
    @MessageMapping("/game-update")
    @SendTo("/topic/updates")
    public String sendUpdate(String updateMessage) throws Exception {
        // You can add logic here (e.g., processing the update)
        return updateMessage;
    }
}
