package com.collegeshowdown.poker_project.security;

import com.collegeshowdown.poker_project.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompAuthInterceptor.class);
    private final JwtUtil jwtUtil;

    public StompAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("STOMP CONNECT without valid Authorization header");
            return message;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            logger.warn("STOMP CONNECT with invalid JWT");
            return message;
        }

        Claims claims = jwtUtil.validateAndParse(token);
        String userId = claims.getSubject();
        String name = claims.get("name", String.class);

        // Set principal so @MessageMapping methods can call message.getPrincipal().getName()
        accessor.setUser(new StompPrincipal(userId, name));
        logger.info("STOMP CONNECT authenticated: userId={} name={}", userId, name);

        return message;
    }

    /** Simple Principal implementation carrying userId as name. */
    public record StompPrincipal(String userId, String name) implements Principal {
        @Override
        public String getName() { return userId; }
    }
}
