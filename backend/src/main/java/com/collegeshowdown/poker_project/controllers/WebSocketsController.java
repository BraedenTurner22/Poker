package com.collegeshowdown.poker_project.controllers;

import com.collegeshowdown.poker_project.domain.lobby.Lobby;
import com.collegeshowdown.poker_project.domain.player.PlayerAction;
import com.collegeshowdown.poker_project.dto.ActionMessage;
import com.collegeshowdown.poker_project.dto.ChatBroadcast;
import com.collegeshowdown.poker_project.dto.ChatMessage;
import com.collegeshowdown.poker_project.dto.GameStateDto;
import com.collegeshowdown.poker_project.service.GameStateBuilder;
import com.collegeshowdown.poker_project.services.ChatModerationService;
import com.collegeshowdown.poker_project.services.LobbyGameService;
import com.collegeshowdown.poker_project.services.LobbyManagerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class WebSocketsController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketsController.class);

    private final LobbyManagerService lobbyManagerService;
    private final LobbyGameService lobbyGameService;
    private final GameStateBuilder gameStateBuilder;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatModerationService chatModerationService;

    /** sessionId → lobbyId (for cleanup on disconnect) */
    private final Map<String, String> sessionLobbyMap = new ConcurrentHashMap<>();
    /** sessionId → userId */
    private final Map<String, Integer> sessionUserMap = new ConcurrentHashMap<>();
    /** "lobbyId:userId" → most recent sessionId — lets a reconnect cancel a pending eviction. */
    private final Map<String, String> activeSessionByLobbyUser = new ConcurrentHashMap<>();

    private static final int DISCONNECT_GRACE_SECONDS = 20;
    private final ScheduledExecutorService disconnectScheduler = Executors.newScheduledThreadPool(2);

    public WebSocketsController(LobbyManagerService lobbyManagerService,
            LobbyGameService lobbyGameService,
            GameStateBuilder gameStateBuilder,
            SimpMessagingTemplate messagingTemplate,
            ChatModerationService chatModerationService) {
        this.lobbyManagerService = lobbyManagerService;
        this.lobbyGameService = lobbyGameService;
        this.gameStateBuilder = gameStateBuilder;
        this.messagingTemplate = messagingTemplate;
        this.chatModerationService = chatModerationService;
    }



    /**
     * Client sends this immediately after subscribing to confirm their seat.
     * The REST /play endpoint already placed them in the lobby; this wires up
     * the WebSocket session to that seat so the server can send them private messages.
     *
     * Destination: /app/game/{lobbyId}/join
     */
    @MessageMapping("/game/{lobbyId}/join")
    public void joinGame(@DestinationVariable String lobbyId,
            StompHeaderAccessor headerAccessor, Principal principal) {

        if (principal == null) {
            logger.warn("Unauthenticated JOIN for lobby {}", lobbyId);
            return;
        }

        int userId = Integer.parseInt(principal.getName());
        String sessionId = headerAccessor.getSessionId();

        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) {
            logger.warn("JOIN for unknown lobby {}", lobbyId);
            sendError(sessionId, lobbyId, "Lobby not found.");
            return;
        }

        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) {
            logger.warn("User {} not found in lobby {}", userId, lobbyId);
            sendError(sessionId, lobbyId, "You are not seated in this lobby.");
            return;
        }

        // Track this session for disconnect cleanup, and mark it as the latest
        // session for this lobby+user so a pending grace-period eviction (if any)
        // from a previous session knows to back off.
        sessionLobbyMap.put(sessionId, lobbyId);
        sessionUserMap.put(sessionId, userId);
        activeSessionByLobbyUser.put(lobbyId + ":" + userId, sessionId);

        logger.info("User {} joined lobby {} at seat {} (session {})", userId, lobbyId, seat, sessionId);

        // Send the current game state to the joining client
        GameStateDto state = gameStateBuilder.build(lobby);
        messagingTemplate.convertAndSend("/topic/game/" + lobbyId + "/state", state);

        // If a hand is already underway (it may have started before this client
        // finished subscribing), deliver their hole cards immediately.
        var player = lobby.getPlayersAtTable()[seat];
        if (player != null && !player.getCards().isEmpty()) {
            messagingTemplate.convertAndSend(
                    "/topic/game/" + lobbyId + "/private/" + userId,
                    gameStateBuilder.buildPrivate(player));
        }
    }



    /**
     * Client sends a game action (fold/check/call/raise/all-in).
     *
     * Destination: /app/game/{lobbyId}/action
     * Payload: { "action": "CALL", "amount": 0 }
     */
    @MessageMapping("/game/{lobbyId}/action")
    public void playerAction(@DestinationVariable String lobbyId,
            @Payload ActionMessage msg, Principal principal) {

        if (principal == null) {
            logger.warn("Unauthenticated action in lobby {}", lobbyId);
            return;
        }

        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) {
            logger.warn("Action for unknown lobby {}", lobbyId);
            return;
        }

        int userId = Integer.parseInt(principal.getName());
        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) {
            logger.warn("Action from user {} who is not seated in lobby {}", userId, lobbyId);
            return;
        }

        PlayerAction action;
        try {
            action = PlayerAction.valueOf(msg.action().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown action '{}' from user {} in lobby {}", msg.action(), userId, lobbyId);
            return;
        }

        logger.info("Action in lobby {} seat {}: {} amount={}", lobbyId, seat, action, msg.amount());
        lobbyGameService.processPlayerAction(lobby, seat, action, msg.amount());
    }



    /**
     * Client sends a chat message.
     *
     * Destination: /app/game/{lobbyId}/chat
     * Payload: { "message": "nice hand!" }
     */
    @MessageMapping("/game/{lobbyId}/chat")
    public void chat(@DestinationVariable String lobbyId,
            @Payload ChatMessage msg, Principal principal) {

        if (principal == null || msg.message() == null || msg.message().isBlank()) return;

        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) return;

        int userId = Integer.parseInt(principal.getName());
        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) return;

        String name = lobby.getPlayersAtTable()[seat].playerRecord.getName();
        String text = msg.message().strip();
        if (text.length() > 200) text = text.substring(0, 200);

        if (chatModerationService.containsBlockedTerm(text)) {
            messagingTemplate.convertAndSend(
                    "/topic/game/" + lobbyId + "/chat-blocked/" + userId,
                    Map.of("reason", "Message blocked: slurs aren't allowed here."));
            return;
        }

        ChatBroadcast broadcast = new ChatBroadcast(userId, name, text,
                System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/game/" + lobbyId + "/chat", broadcast);
    }



    /**
     * Client explicitly leaves the table (back to the home screen).
     *
     * Destination: /app/game/{lobbyId}/leave
     */
    @MessageMapping("/game/{lobbyId}/leave")
    public void leaveGame(@DestinationVariable String lobbyId,
            StompHeaderAccessor headerAccessor, Principal principal) {

        if (principal == null) return;
        int userId = Integer.parseInt(principal.getName());
        String sessionId = headerAccessor.getSessionId();

        sessionLobbyMap.remove(sessionId);
        sessionUserMap.remove(sessionId);
        activeSessionByLobbyUser.remove(lobbyId + ":" + userId, sessionId);

        removePlayerFromLobby(lobbyId, userId);
    }



    /**
     * Handles WebSocket disconnect. A reload (or a brief network blip — the
     * STOMP client auto-reconnects) closes the socket the same way a real
     * departure does, so we don't evict the seat immediately. Instead we wait
     * a short grace period and only remove them if no newer session for this
     * lobby+user has registered by then (i.e. they never came back).
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        String lobbyId = sessionLobbyMap.remove(sessionId);
        Integer userId = sessionUserMap.remove(sessionId);

        if (lobbyId == null || userId == null) return;

        String key = lobbyId + ":" + userId;
        disconnectScheduler.schedule(() -> {
            try {
                // If a newer session has since registered for this lobby+user,
                // they reconnected — leave their seat alone.
                if (!activeSessionByLobbyUser.remove(key, sessionId)) return;
                removePlayerFromLobby(lobbyId, userId);
            } catch (Exception e) {
                logger.error("Grace-period eviction failed for lobby {} user {}", lobbyId, userId, e);
            }
        }, DISCONNECT_GRACE_SECONDS, TimeUnit.SECONDS);
    }



    /** Shared leave/disconnect path: fold if in a hand, vacate the seat, broadcast. */
    private void removePlayerFromLobby(String lobbyId, int userId) {
        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) return;

        int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
        if (seat == -1) return;

        logger.info("User {} leaving lobby {} seat {}", userId, lobbyId, seat);

        // Fold them out and settle the hand if they were the last opponent.
        // An ALL_IN departure defers seat removal until the hand concludes
        // (handled by LobbyGameService itself) so they keep their pot equity —
        // in that case this returns false and the seat must stay put for now.
        boolean canVacateNow = lobbyGameService.handlePlayerDeparture(lobby, seat);

        if (canVacateNow) {
            lobbyManagerService.leaveTable(lobby, seat);
        }
        lobbyGameService.broadcastState(lobby);
    }



    private void sendError(String sessionId, String lobbyId, String message) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId + "/error",
                Map.of("error", message));
    }
}
