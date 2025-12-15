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
                // 기본 설정들 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // URL 별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/api/auth/login",
                                "/api/auth/signup",
                                "/member/login",
                                "/member/signup",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // 인증/인가 실패 시 JSON 응답
                .exceptionHandling(exception -> exception

                        // 인증 실패(로그인 안 됨) → 401
                        .authenticationEntryPoint((request, response, authException) -> {
                            ErrorCode errorCode = ErrorCode.LOGIN_REQUIRED;
                            HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

                            ResponseDto<Map<String, Object>> body = new ResponseDto<>(
                                    status.value(),
                                    errorCode.getMsg(),
                                    Map.of(
                                            "code", errorCode.getCode(),
                                            "path", request.getRequestURI()
                                    )
                            );

                            response.setStatus(status.value());
                            response.setContentType("application/json;charset=UTF-8");

                            String json = new ObjectMapper().writeValueAsString(body);
                            response.getWriter().write(json);
                        })

                        // 인가 실패(권한 부족) → 403
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
                            HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

                            ResponseDto<Map<String, Object>> body = new ResponseDto<>(
                                    status.value(),
                                    errorCode.getMsg(),
                                    Map.of(
                                            "code", errorCode.getCode(),
                                            "path", request.getRequestURI()
                                    )
                            );

                            response.setStatus(status.value());
                            response.setContentType("application/json;charset=UTF-8");

                            String json = new ObjectMapper().writeValueAsString(body);
                            response.getWriter().write(json);
                        })
                )

                // JWT 인증 필터 등록
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
