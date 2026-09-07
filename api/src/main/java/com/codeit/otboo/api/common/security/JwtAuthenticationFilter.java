package com.codeit.otboo.api.common.security;

import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && tokenProvider.validate(token)) {
            authenticate(token);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }

    private void authenticate(String token) {
        Claims claims = tokenProvider.parseClaims(token);
        UUID userId = UUID.fromString(claims.getSubject());
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);

        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return;
        }

        User user = found.get();

        if (!user.getTokenVersion().equals(tokenVersion)) {
            log.debug("토큰 버전 불일치 - 무효 처리");
            return;
        }

        if (Boolean.TRUE.equals(user.getLocked())) {
            log.debug("잠긴 계정 - 인증 거부");
            return;
        }

        CustomUserDetails principal = new CustomUserDetails(
                user.getId(), user.getEmail(), user.getRole().name());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put("userId", user.getId().toString());
    }
}
