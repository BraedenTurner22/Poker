package com.collegeshowdown.poker_project.security;

import com.collegeshowdown.poker_project.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP CONNECT frames and authorizes every SUBSCRIBE / SEND.
 *
 * <ul>
 *   <li>CONNECT must carry a valid JWT, otherwise the connection is refused.</li>
 *   <li>SEND may only target {@code /app/**}. The in-memory SimpleBroker would
 *       otherwise relay a client SEND to {@code /topic/**} straight to every
 *       subscriber, letting anyone forge game state, chat, or kick messages.</li>
 *   <li>SUBSCRIBE may not use broker wildcards ({@code *}, {@code ?}, {@code {}})
 *       — {@code /topic/**} would receive every table's traffic — and the
 *       per-user topics ({@code /topic/game/{lobby}/private/{userId}} etc.,
 *       which carry hole cards) only to the user they belong to.</li>
 * </ul>
 * Throwing here makes Spring answer with a STOMP ERROR frame and drop the frame.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompAuthInterceptor.class);

    /** Per-user topics: /topic/game/{lobbyId}/{private|chat-blocked|kick|error}/{userId} */
    private static final Pattern PER_USER_TOPIC =
            Pattern.compile("^/topic/game/[^/]+/(?:private|chat-blocked|kick|error)/([^/]+)$");

    private final JwtUtil jwtUtil;

    public StompAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("STOMP CONNECT without valid Authorization header — rejected");
            throw new MessageDeliveryException("Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            logger.warn("STOMP CONNECT with invalid JWT — rejected");
            throw new MessageDeliveryException("Invalid or expired token");
        }

        Claims claims = jwtUtil.validateAndParse(token);
        String userId = claims.getSubject();
        String name = claims.get("name", String.class);

        // Set principal so @MessageMapping methods can call message.getPrincipal().getName()
        accessor.setUser(new StompPrincipal(userId, name));
        logger.info("STOMP CONNECT authenticated: userId={} name={}", userId, name);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();
        if (user == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        if (destination == null
                || !(destination.startsWith("/topic/") || destination.startsWith("/user/"))
                || destination.indexOf('*') >= 0
                || destination.indexOf('?') >= 0
                || destination.indexOf('{') >= 0) {
            logger.warn("User {} denied SUBSCRIBE to {}", user.getName(), destination);
            throw new MessageDeliveryException("Subscription not allowed: " + destination);
        }
        Matcher m = PER_USER_TOPIC.matcher(destination);
        if (m.matches() && !m.group(1).equals(user.getName())) {
            logger.warn("User {} denied SUBSCRIBE to another user's topic {}", user.getName(), destination);
            throw new MessageDeliveryException("Subscription not allowed: " + destination);
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();
        if (user == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        if (destination == null || !destination.startsWith("/app/")) {
            logger.warn("User {} denied SEND to {}", user.getName(), destination);
            throw new MessageDeliveryException("Send not allowed: " + destination);
        }
    }

    /** Simple Principal implementation carrying userId as name. */
    public record StompPrincipal(String userId, String name) implements Principal {
        @Override
        public String getName() { return userId; }
    }
}
