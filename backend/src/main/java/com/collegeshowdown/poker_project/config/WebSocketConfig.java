package com.collegeshowdown.poker_project.config;

import com.collegeshowdown.poker_project.security.StompAuthInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor) {
        this.stompAuthInterceptor = stompAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        // The outbound channel is a thread pool: without this, two messages to
        // the same session (e.g. a new hand's public state and its private
        // hole cards) can reach the client in either order.
        config.setPreservePublishOrder(true);
        // Deliberately NOT StompEndpointRegistry.setPreserveReceiveOrder(true):
        // its OrderedMessageChannelDecorator catches and only logs exceptions
        // thrown by inbound interceptors, so StompAuthInterceptor's rejection
        // of an expired/invalid token would no longer produce a STOMP ERROR
        // frame — the client would hang on CONNECT instead of being sent back
        // to sign in. (The SUBSCRIBE-vs-join race it would fix is already
        // covered by the REST play/rejoin response carrying the initial state.)
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same allowlist as the HTTP API (app.cors.allowed-origins) — every
        // action/chat/join still requires a valid JWT regardless of origin,
        // so this isn't an auth boundary, just kept consistent with the HTTP
        // CORS policy rather than left wide open.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(java.util.Arrays.stream(allowedOrigins.split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
