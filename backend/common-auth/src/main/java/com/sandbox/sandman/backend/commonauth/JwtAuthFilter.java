package com.sandbox.sandman.backend.commonauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Extracts Supabase UID from the JWT in `Authorization: Bearer ...`, resolves it to an
 * internal users.id (chat_app.users), and attaches both as request attributes.
 *
 * Endpoints decide whether the user is required (CurrentUser.requireUserId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthUserRepository authUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID supabaseUid = JwtDecoder.extractSupabaseUid(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (supabaseUid != null) {
            request.setAttribute(CurrentUser.ATTR_SUPABASE_UID, supabaseUid.toString());
            Optional<AuthUser> user = authUserRepository.findBySupabaseUid(supabaseUid);
            user.ifPresent(u -> request.setAttribute(CurrentUser.ATTR_USER_ID, u.getId()));
            if (user.isEmpty()) {
                log.warn("JWT decoded sub={} but no chat_app.users row exists; user-service sync may be missing", supabaseUid);
            }
        }
        chain.doFilter(request, response);
    }
}
