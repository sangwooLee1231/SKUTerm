package com.sku.common.interceptor;

import com.sku.queue.service.QueueService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAccessInterceptor implements HandlerInterceptor {

    private final QueueService queueService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 쿠키에서 queueToken 찾기
        String queueToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("queueToken".equals(cookie.getName())) {
                    queueToken = cookie.getValue();
                    break;
                }
            }
        }

        // 토큰이 없거나, 활성 상태(Active)가 아니면 -> 대기열 페이지로 쫓아냄
        try {
            if (queueToken == null) {
                throw new Exception("토큰 없음");
            }
            // 서비스에서 토큰 검증 (Active가 아니면 예외 발생함)
            queueService.validateActiveToken(queueToken);

            // 통과!
            return true;

        } catch (Exception e) {
            log.info("대기열 검증 실패(접근 차단): uri={}, token={}", request.getRequestURI(), queueToken);
            response.sendRedirect("/queue/waiting");
            return false;
        }
    }
}