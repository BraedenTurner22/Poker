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

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
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
    /**
     * "lobbyId:userId" → session of the most recent join or (last-session)
     * disconnect for that seat. A grace-period eviction only proceeds if it is
     * still the latest event, so a reconnect (newer join) cancels it.
     */
    private final Map<String, String> activeSessionByLobbyUser = new ConcurrentHashMap<>();
    /**
     * "lobbyId:userId" → every currently-connected session joined to that seat.
     * The same user can have several (two tabs, or a reload whose new socket
     * joins before the old one's disconnect is processed); closing one of them
     * must not evict the seat while another is still connected.
     */
    private final Map<String, Set<String>> liveSessionsByLobbyUser = new ConcurrentHashMap<>();
    /**
     * Sessions whose STOMP CONNECT was accepted and that haven't disconnected.
     * Inbound frames run on an executor, but SessionDisconnectEvent fires on
     * the transport thread — a JOIN still queued (e.g. waiting on a busy
     * lobby lock) when its socket closes is processed after the disconnect.
     * Registering it then would leave a dead session in
     * liveSessionsByLobbyUser forever, and no later disconnect would ever
     * start the seat's grace-period eviction again.
     */
    private final Set<String> connectedSessions = ConcurrentHashMap.newKeySet();
    /**
     * Makes joinGame's "still connected?" check plus its session registration
     * atomic with handleDisconnect's cleanup. Taken inside a lobby lock
     * (joinGame) or on its own — never while acquiring a lobby lock.
     */
    private final Object sessionLock = new Object();

    private static final int DISCONNECT_GRACE_SECONDS = 20;
    private final ScheduledExecutorService disconnectScheduler = Executors.newScheduledThreadPool(2);
    private long disconnectGraceMillis = DISCONNECT_GRACE_SECONDS * 1000L;

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

    /** Test hook — shortens the disconnect grace period. */
    void setDisconnectGraceMillis(long millis) {
        this.disconnectGraceMillis = millis;
    }

    @PreDestroy
    void shutdown() {
        disconnectScheduler.shutdownNow();
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
            sendError(userId, lobbyId, "Lobby not found.");
            return;
        }

        // Everything below reads (and the pending-departure cancel mutates)
        // lobby state, so it runs under the lobby lock like the game engine.
        synchronized (lobby) {
            int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
            if (seat == -1) {
                logger.warn("User {} not found in lobby {}", userId, lobbyId);
                sendError(userId, lobbyId, "You are not seated in this lobby.");
                return;
            }

            // Track this session for disconnect cleanup, and mark it as the latest
            // session for this lobby+user so a pending grace-period eviction (if any)
            // from a previous session knows to back off.
            String key = lobbyId + ":" + userId;
            synchronized (sessionLock) {
                if (!connectedSessions.contains(sessionId)) {
                    // This JOIN outlived its socket (see connectedSessions).
                    // Treat it as a join immediately followed by a disconnect:
                    // if no other session holds the seat, start its grace period.
                    logger.info("Ignoring JOIN from already-disconnected session {} (lobby {}, user {})",
                            sessionId, lobbyId, userId);
                    if (!liveSessionsByLobbyUser.containsKey(key)) {
                        activeSessionByLobbyUser.put(key, sessionId);
                        scheduleEviction(key, lobbyId, userId, sessionId);
                    }
                    return;
                }
                String previousLobby = sessionLobbyMap.put(sessionId, lobbyId);
                if (previousLobby != null && !previousLobby.equals(lobbyId)) {
                    removeLiveSession(previousLobby + ":" + userId, sessionId);
                }
                sessionUserMap.put(sessionId, userId);
                // The add must happen inside compute(): computeIfAbsent(...).add()
                // can add to a set that a concurrent disconnect (not under the
                // lobby lock) has just emptied and dropped from the map, leaving
                // this live session invisible to the grace-period check.
                liveSessionsByLobbyUser.compute(key, (k, sessions) -> {
                    Set<String> s = sessions != null ? sessions : ConcurrentHashMap.newKeySet();
                    s.add(sessionId);
                    return s;
                });
                activeSessionByLobbyUser.put(key, sessionId);
            }

            // They came back after their grace period expired while ALL_IN:
            // the seat was only marked for removal at hand end — keep it.
            lobby.getPendingDepartureSeats().remove(seat);

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

        PlayerAction action;
        try {
            action = PlayerAction.valueOf(msg.action().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Unknown action '{}' from user {} in lobby {}",
                    msg == null ? null : msg.action(), userId, lobbyId);
            return;
        }

        // Seat lookup and the action must be atomic: otherwise the seat could be
        // vacated (and re-filled by someone else) in between, and the action
        // would be applied to the wrong player.
        synchronized (lobby) {
            int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
            if (seat == -1) {
                logger.warn("Action from user {} who is not seated in lobby {}", userId, lobbyId);
                return;
            }

            logger.info("Action in lobby {} seat {}: {} amount={}", lobbyId, seat, action, msg.amount());
            lobbyGameService.processPlayerAction(lobby, seat, action, msg.amount());
        }
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

        if (principal == null || msg == null || msg.message() == null || msg.message().isBlank()) return;

        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) return;

        int userId = Integer.parseInt(principal.getName());
        String name;
        synchronized (lobby) {
            int seat = lobbyManagerService.getSeatForPlayer(lobby, userId);
            if (seat == -1) return;
            name = lobby.getPlayersAtTable()[seat].playerRecord.getName();
        }

        String text = msg.message().strip();
        if (text.length() > 200) {
            // Don't split a surrogate pair (emoji) at the cut point.
            int end = Character.isHighSurrogate(text.charAt(199)) ? 199 : 200;
            text = text.substring(0, end);
        }

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
        String key = lobbyId + ":" + userId;

        synchronized (sessionLock) {
            // Only forget the session if it joined this lobby — a LEAVE naming
            // another lobby must not unmap it, or its disconnect would never
            // start the grace-period eviction for the seat it actually holds.
            if (sessionLobbyMap.remove(sessionId, lobbyId)) {
                sessionUserMap.remove(sessionId);
            }
            removeLiveSession(key, sessionId);
            activeSessionByLobbyUser.remove(key, sessionId);
        }

        removePlayerFromLobby(lobbyId, userId);
    }



    /** Published on the transport thread once a CONNECT passes StompAuthInterceptor. */
    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (sessionId != null) connectedSessions.add(sessionId);
    }

    /**
     * Handles WebSocket disconnect. A reload (or a brief network blip — the
     * STOMP client auto-reconnects) closes the socket the same way a real
     * departure does, so we don't evict the seat immediately. Instead we wait
     * a short grace period and only remove them if no session for this
     * lobby+user is connected by then (i.e. they never came back, and no other
     * tab is still open on the seat).
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        synchronized (sessionLock) {
            connectedSessions.remove(sessionId);

            String lobbyId = sessionLobbyMap.remove(sessionId);
            Integer userId = sessionUserMap.remove(sessionId);

            if (lobbyId == null || userId == null) return;

            String key = lobbyId + ":" + userId;

            // Another tab / newer socket for this seat is still connected — the
            // seat isn't abandoned. That session's own disconnect starts the grace period.
            if (removeLiveSession(key, sessionId)) return;

            // This was the last live session: it becomes the latest event for the
            // seat, so only a join that happens after it can cancel this eviction.
            activeSessionByLobbyUser.put(key, sessionId);
            scheduleEviction(key, lobbyId, userId, sessionId);
        }
    }

    /** Evicts the seat after the grace period unless a session has (re)joined it since. */
    private void scheduleEviction(String key, String lobbyId, int userId, String sessionId) {
        disconnectScheduler.schedule(() -> {
            try {
                // If a session has since (re)joined this lobby+user, or a later
                // disconnect has its own grace period running, leave the seat alone.
                if (liveSessionsByLobbyUser.containsKey(key)) return;
                if (!activeSessionByLobbyUser.remove(key, sessionId)) return;
                removePlayerFromLobby(lobbyId, userId);
            } catch (Exception e) {
                logger.error("Grace-period eviction failed for lobby {} user {}", lobbyId, userId, e);
            }
        }, disconnectGraceMillis, TimeUnit.MILLISECONDS);
    }



    /**
     * Removes a session from a seat's live-session set (dropping the set once
     * empty). Returns true if other live sessions for that seat remain.
     */
    private boolean removeLiveSession(String key, String sessionId) {
        Set<String> remaining = liveSessionsByLobbyUser.computeIfPresent(key, (k, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        return remaining != null;
    }



    /** Shared leave/disconnect path: fold if in a hand, vacate the seat, broadcast. */
    private void removePlayerFromLobby(String lobbyId, int userId) {
        Lobby lobby = lobbyManagerService.getLobbyById(lobbyId);
        if (lobby == null) return;

        // Seat lookup, departure, and vacating are one atomic step: two
        // concurrent removals for the same user (explicit leave in one tab +
        // grace-period eviction of another) could otherwise both resolve the
        // same seat index, and the second would cash out whoever sat down there
        // in between.
        synchronized (lobby) {
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
    }



    /** Sent on the requesting user's own error topic, not broadcast to the whole table. */
    private void sendError(int userId, String lobbyId, String message) {
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId + "/error/" + userId,
                Map.of("error", message));
    }
}
