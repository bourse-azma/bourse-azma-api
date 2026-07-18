package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.service.MarketSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AppUserDetailsService userDetailsService;
    private final RevokedTokenService revokedTokenService;
    private final MarketSubscriptionRegistry marketSubscriptionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(
                    accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION),
                    accessor.getUser()
            ));
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            marketSubscriptionRegistry.removeSession(accessor.getSessionId());
            return message;
        }

        requireAuthenticated(accessor.getUser());
        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor.getDestination());
            marketSubscriptionRegistry.register(
                    accessor.getSessionId(),
                    accessor.getSubscriptionId(),
                    accessor.getDestination()
            );
        } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
            marketSubscriptionRegistry.unregister(accessor.getSessionId(), accessor.getSubscriptionId());
        } else if (StompCommand.SEND.equals(command)) {
            throw new BadCredentialsException("Client STOMP SEND frames are not allowed.");
        }
        return message;
    }

    private Authentication authenticate(String authorizationHeader, java.security.Principal handshakePrincipal) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            if (handshakePrincipal instanceof Authentication authentication && authentication.isAuthenticated()) {
                return authentication;
            }
            throw new BadCredentialsException(
                    "A Bearer token or an authenticated JWT handshake cookie is required."
            );
        }
        if (!authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BadCredentialsException("The STOMP Authorization header must use the Bearer scheme.");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || !jwtTokenService.isValid(token) || revokedTokenService.isRevoked(token)) {
            throw new BadCredentialsException("The WebSocket access token is invalid or revoked.");
        }

        try {
            AppUserPrincipal principal = userDetailsService.loadUserById(jwtTokenService.extractUserId(token));
            if (!principal.isEnabled() || principal.getTokenVersion() != jwtTokenService.extractTokenVersion(token)) {
                throw new BadCredentialsException("The WebSocket access token is no longer valid.");
            }
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("The WebSocket user no longer exists.", ex);
        }
    }

    private void requireAuthenticated(java.security.Principal principal) {
        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("An authenticated WebSocket session is required.");
        }
    }

    private void authorizeSubscription(String destination) {
        if (destination == null) {
            throw new BadCredentialsException("A STOMP subscription destination is required.");
        }
        String normalized = destination.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/topic/market/")
                || normalized.startsWith("/topic/market-overview/")
                || normalized.startsWith("/user/queue/")) {
            return;
        }
        throw new BadCredentialsException("Subscription to this STOMP destination is not allowed.");
    }
}
