package com.sku.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sku.common.dto.ResponseDto;
import com.sku.common.filter.JwtAuthenticationFilter;
import com.sku.common.jwt.JwtTokenProvider;
import com.sku.common.util.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/error",
                                "/login-required",
                                "/favicon.ico",
                                "/api/auth/login",
                                "/api/auth/signup",
                                "/api/auth/refresh",
                                "/member/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/health"
                        ).permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();

                            if (uri.startsWith("/api/")) {
                                ErrorCode errorCode = ErrorCode.LOGIN_REQUIRED;
                                HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

                                ResponseDto<Map<String, Object>> body = new ResponseDto<>(
                                        status.value(),
                                        errorCode.getMsg(),
                                        Map.of("code", errorCode.getCode(), "path", uri)
                                );

                                response.setStatus(status.value());
                                response.setContentType("application/json;charset=UTF-8");
                                new ObjectMapper().writeValue(response.getWriter(), body);
                            }
                            else {
                                response.sendRedirect("/login-required");
                            }
                        })

                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
                            HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

                            ResponseDto<Map<String, Object>> body = new ResponseDto<>(
                                    status.value(),
                                    errorCode.getMsg(),
                                    Map.of("code", errorCode.getCode(), "path", request.getRequestURI())
                            );

                            response.setStatus(status.value());
                            response.setContentType("application/json;charset=UTF-8");
                            new ObjectMapper().writeValue(response.getWriter(), body);
                        })
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}