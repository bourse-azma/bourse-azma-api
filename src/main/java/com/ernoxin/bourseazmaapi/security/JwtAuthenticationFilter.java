package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.service.UserActivityService;
import com.ernoxin.bourseazmaapi.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AppUserDetailsService userDetailsService;
    private final RevokedTokenService revokedTokenService;
    private final AuthCookieService authCookieService;
    private final UserActivityService userActivityService;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = authCookieService.extractAccessToken(request, request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null || !jwtTokenService.isValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (revokedTokenService.isRevoked(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long authenticatedUserId = null;
        try {
            Long userId = jwtTokenService.extractUserId(token);
            AppUserPrincipal principal = userDetailsService.loadUserById(userId);
            if (!principal.isEnabled() || principal.getTokenVersion() != jwtTokenService.extractTokenVersion(token)) {
                filterChain.doFilter(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            authenticatedUserId = userId;
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);

        if (authenticatedUserId != null) {
            try {
                userActivityService.touch(authenticatedUserId);
                if ("POST".equalsIgnoreCase(request.getMethod())
                        && "/api/v1/auth/logout".equals(request.getRequestURI())
                        && response.getStatus() >= 200 && response.getStatus() < 300) {
                    userActivityService.record(authenticatedUserId, "LOGOUT", clientIpResolver.resolve(request));
                }
            } catch (RuntimeException ignored) {
                // Activity tracking must never break the user's main request.
            }
        }
    }
}
