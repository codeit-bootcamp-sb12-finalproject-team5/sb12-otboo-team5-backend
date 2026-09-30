package com.codeit.otboo.api.common.config;

import com.codeit.otboo.api.auth.service.CustomOidcUserService;
import com.codeit.otboo.api.common.security.JwtAuthenticationFilter;
import com.codeit.otboo.api.common.security.OAuthLoginFailureHandler;
import com.codeit.otboo.api.common.security.OAuthLoginSuccessHandler;
import com.codeit.otboo.api.common.security.SecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final CustomOidcUserService customOidcUserService;
    private final OAuthLoginSuccessHandler oauthLoginSuccessHandler;
    private final OAuthLoginFailureHandler oauthLoginFailureHandler;

    /* 신규 OAuth2 OIDC 로그인 처리용 */
    @Bean
    @Order(1)
    public SecurityFilterChain oauthSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .securityMatcher(
                        "/oauth2/**",
                        "/login/oauth2/**"
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                        .successHandler(oauthLoginSuccessHandler)
                        .failureHandler(oauthLoginFailureHandler)
                );

        return http.build();
    }

    /* 기존 JWT기반 인증처리용 */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // JWT 사용이므로 세션을 만들지 않음
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF 토큰을 쿠키로 관리 (XSRF-TOKEN)
            // CSRF 토큰을 쿠키로 관리 (XSRF-TOKEN)
            // Spring Security 6 의 기본 BREACH 방어는 헤더 값과 쿠키 값이 달라지므로,
            // 쿠키 값을 그대로 사용하도록 기본 핸들러를 명시합니다.
//              .csrf(csrf -> csrf
//                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
//                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
//                        .ignoringRequestMatchers("/api/auth/**", "/api/users"))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // 401 / 403 응답을 공통 ErrorResponse 형식으로
            .exceptionHandling(handler -> handler
                .authenticationEntryPoint(securityExceptionHandler)
                .accessDeniedHandler(securityExceptionHandler))

            .authorizeHttpRequests(auth -> auth
                // 모니터링 수집용. Prometheus는 토큰을 갱신할 수 없으므로 JWT를 요구하지 않는다.
                // 노출한 엔드포인트(health, prometheus)만 열리며, 배포 시에는 이 포트를 외부에 공개하지 않는다.
                .requestMatchers(EndpointRequest.to("health", "prometheus")).permitAll()

                // 인증 없이 접근 가능
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                // STOMP CONNECT 인증은 WebSocket 채널 인터셉터에서 처리할 예정
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers("/error").permitAll()

                // 관리자 전용
                .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                .requestMatchers("/api/users/*/role").hasRole("ADMIN")
                .requestMatchers("/api/users/*/lock").hasRole("ADMIN")

                // 유저 전용
                //todo: 테스트 끝내고 아래 권한 수정 필요
                .requestMatchers("/api/recommendations/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/outfit/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/feeds/**").hasAnyRole("USER", "ADMIN")

                // 그 외 전부 인증 필요
                .anyRequest().authenticated())

            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
