package com.flairbit.chats.filter;

import com.flairbit.chats.security.FlairbitTokenVerifier;
import com.flairbit.chats.utils.ErrorUtility;
import com.nimbusds.jwt.JWTClaimsSet;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final FlairbitTokenVerifier tokenVerifier;

    public AuthFilter(FlairbitTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (Objects.nonNull(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JWTClaimsSet claims = tokenVerifier.verifyAndExtract(token);
                String username = claims.getSubject();

                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("USER"));

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(username, null, authorities)
                );
            } catch (AccessDeniedException ex) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorUtility.printError("Access Denied: " + ex.getMessage(), response);

            } catch (IllegalArgumentException | ExpiredJwtException | MalformedJwtException ex) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorUtility.printError("Authentication Failed: " + ex.getMessage(), response);

            } catch (Exception ex) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorUtility.printError("Internal Server Error: " + ex.getMessage(), response);
            }
        }

        filterChain.doFilter(request, response);
    }
}
