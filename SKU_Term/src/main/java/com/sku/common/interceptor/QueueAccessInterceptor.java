package com.sku.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sku.common.dto.ResponseDto;
import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.queue.dto.QueueStatusResponseDto;
import com.sku.queue.service.QueueService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAccessInterceptor implements HandlerInterceptor {

    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    @Value("${peakguard.queue.enabled:true}")
    private boolean queueEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (!queueEnabled) {
            return true;
        }

        String uri = request.getRequestURI();

        String queueToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("queueToken".equals(cookie.getName())) {
                    queueToken = cookie.getValue();
                    break;
                }
            }
        }

        boolean isApiRequest = uri != null && uri.startsWith("/api/");

        try {
            if (queueToken == null || queueToken.isBlank()) {
                if (isApiRequest) {
                    writeApiError(
                            response,
                            ErrorCode.QUEUE_TOKEN_INVALID,
                            "대기열 토큰이 없습니다. 다시 진입해주세요.",
                            uri,
                            null
                    );
                } else {
                    response.sendRedirect("/queue/waiting");
                }
                return false;
            }

            queueService.validateActiveToken(queueToken);
            return true;

        } catch (CustomException ce) {
            if (!isApiRequest) {
                log.info("대기열 검증 실패(리다이렉트): uri={}, token={}", uri, maskToken(queueToken));
                response.sendRedirect("/queue/waiting");
                return false;
            }

            ErrorCode errorCode = ce.getErrorCode();
            String overrideMsg = null;
            Map<String, Object> extra = null;

            if (errorCode == ErrorCode.QUEUE_NOT_ACTIVE) {
                try {
                    QueueStatusResponseDto status = queueService.getStatus(queueToken);
                    extra = Map.of("queueStatus", status);
                } catch (CustomException statusEx) {
                    if (statusEx.getErrorCode() == ErrorCode.QUEUE_TOKEN_NOT_FOUND) {
                        errorCode = ErrorCode.QUEUE_TOKEN_NOT_FOUND;
                        overrideMsg = "대기열 토큰이 만료되었습니다. 다시 진입해주세요.";
                    } else {
                        errorCode = statusEx.getErrorCode();
                    }
                }
            }

            log.info("대기열 검증 실패(API 차단): uri={}, token={}, code={}", uri, maskToken(queueToken), errorCode.getCode());
            writeApiError(response, errorCode, overrideMsg, uri, extra);
            return false;

        } catch (DataAccessException dae) {
            // Redis 장애 등
            if (!isApiRequest) {
                log.error("대기열 검증 실패(스토리지 오류): uri={}, token={}", uri, maskToken(queueToken), dae);
                response.sendRedirect("/queue/waiting");
                return false;
            }

            ErrorCode errorCode = ErrorCode.QUEUE_SERVICE_UNAVAILABLE;
            log.error("대기열 검증 실패(API 스토리지 오류): uri={}, token={}, code={}", uri, maskToken(queueToken), errorCode.getCode(), dae);
            writeApiError(response, errorCode, null, uri, null);
            return false;

        } catch (Exception e) {
            if (!isApiRequest) {
                log.error("대기열 검증 실패(예기치 못한 오류): uri={}, token={}", uri, maskToken(queueToken), e);
                response.sendRedirect("/queue/waiting");
                return false;
            }

            ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            log.error("대기열 검증 실패(API 예기치 못한 오류): uri={}, token={}, code={}", uri, maskToken(queueToken), errorCode.getCode(), e);
            writeApiError(response, errorCode, null, uri, null);
            return false;
        }
    }

    private void writeApiError(
            HttpServletResponse response,
            ErrorCode errorCode,
            String overrideMessage,
            String path,
            Map<String, Object> extraData
    ) throws IOException {

        int status = errorCode.getStatus();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", errorCode.getCode());
        data.put("path", path);

        if (extraData != null && !extraData.isEmpty()) {
            data.putAll(extraData);
        }

        String message = (overrideMessage != null && !overrideMessage.isBlank())
                ? overrideMessage
                : errorCode.getMsg();

        ResponseDto<Map<String, Object>> body = new ResponseDto<>(status, message, data);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String maskToken(String token) {
        if (token == null) return null;
        if (token.length() <= 8) return "********";
        return token.substring(0, 8) + "****";
    }
}
