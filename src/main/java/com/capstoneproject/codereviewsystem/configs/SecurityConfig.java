package com.capstoneproject.codereviewsystem.configs;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.capstoneproject.codereviewsystem.security.JwtAuthenticationEntryPoint;
import com.capstoneproject.codereviewsystem.security.JwtAuthenticationFilter;
import com.capstoneproject.codereviewsystem.security.oauth2.CustomOAuth2UserService;
import com.capstoneproject.codereviewsystem.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.capstoneproject.codereviewsystem.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.capstoneproject.codereviewsystem.security.oauth2.OAuth2AuthenticationSuccessHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint entryPoint;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieRepo;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/auth/**",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/login-success",
                        "/api/webhook/**",
                        "/api/cli/push",
                        "/api/cli/change-token",
                        "/api/cli/token-info",
                        "/api/cli/log",
                        "/avatars/**",
                        "/error"
                ).permitAll()

                .requestMatchers("/api/admin/auth/**").permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .requestMatchers("/api/iam/**").hasAnyRole("ADMIN", "IAM")

                .requestMatchers("/api/models/**").hasAnyRole("ADMIN", "IAM", "USER")

                .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "IAM")

                .requestMatchers(
                        "/api/user/**",
                        "/api/repositories/**",
                        "/api/zip/**",
                        "/api/cli/**"
                ).hasAnyRole("USER", "ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(e -> e
                    .baseUri("/oauth2/authorize")
                    .authorizationRequestRepository(cookieRepo))
                .redirectionEndpoint(e -> e
                    .baseUri("/oauth2/callback/*"))
                .userInfoEndpoint(e -> e
                    .userService(customOAuth2UserService))
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}