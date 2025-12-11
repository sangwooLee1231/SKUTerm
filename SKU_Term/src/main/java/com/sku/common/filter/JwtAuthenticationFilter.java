package com.sku.common.filter;

import com.sku.common.exception.CustomException;
import com.sku.common.jwt.JwtTokenProvider;
import com.sku.common.util.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 쿠키에서 accessToken 추출
        String token = resolveToken(httpRequest);

        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 3. 토큰 유효성 검증 (만료/위조 등 확인)
            jwtTokenProvider.validateToken(token);

            // 4. 인증 객체 생성하여 SecurityContext에 저장
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.SESSION_EXPIRED, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED, e);
        }

        chain.doFilter(request, response);
    }

    // 쿠키에서 "accessToken" 이름의 값을 찾아 반환
    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}