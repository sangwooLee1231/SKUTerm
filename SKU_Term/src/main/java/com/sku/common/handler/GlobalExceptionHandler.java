package com.sku.common.handler;

import com.sku.common.dto.ResponseDto;
import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.sku.common.dto.ErrorResponse;

import javax.naming.AuthenticationException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 핸들러
 * 모든 예외 응답을 {status, message, data} 형식의 ResponseDto로 내려준다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외(CustomException) 처리
     */
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ResponseDto<Map<String, Object>>> handleCustomException(
            CustomException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

        log.warn("CustomException: code={}, msg={}, path={}",
                errorCode.getCode(), errorCode.getMsg(), request.getRequestURI());

        Map<String, Object> data = Map.of(
                "code", errorCode.getCode(),
                "path", request.getRequestURI()
        );

        ResponseDto<Map<String, Object>> body =
                new ResponseDto<>(status.value(), errorCode.getMsg(), data);

        return ResponseEntity.status(status).body(body);
    }

    /**
     * @Valid, @Validated 바인딩 에러 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ResponseDto<Map<String, Object>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

        log.warn("MethodArgumentNotValidException: path={}, message={}",
                request.getRequestURI(), e.getMessage());

        // 필드별 에러 메시지 맵으로 변환
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fieldError -> fieldError.getField(),
                        fieldError -> fieldError.getDefaultMessage(),
                        (msg1, msg2) -> msg1   // 같은 필드가 여러 번 나올 때 첫 번째 메시지 사용
                ));

        Map<String, Object> data = Map.of(
                "code", errorCode.getCode(),
                "path", request.getRequestURI(),
                "errors", fieldErrors
        );

        ResponseDto<Map<String, Object>> body =
                new ResponseDto<>(status.value(), errorCode.getMsg(), data);

        return ResponseEntity.status(status).body(body);
    }

    /**
     * 타입 변환 에러 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ResponseDto<Map<String, Object>>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_TYPE_VALUE;
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

        log.warn("MethodArgumentTypeMismatchException: path={}, message={}",
                request.getRequestURI(), e.getMessage());

        Map<String, Object> data = Map.of(
                "code", errorCode.getCode(),
                "path", request.getRequestURI(),
                "name", e.getName(),          // 파라미터 이름
                "value", String.valueOf(e.getValue()) // 잘못 들어온 값
        );

        ResponseDto<Map<String, Object>> body =
                new ResponseDto<>(status.value(), errorCode.getMsg(), data);

        return ResponseEntity.status(status).body(body);
    }

    /**
     * 처리하지 못한 모든 예외에 대한 fallback 처리
     */

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ResponseDto<Map<String, Object>>> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

        log.error("Unhandled Exception at path={}", request.getRequestURI(), e);

        Map<String, Object> data = Map.of(
                "code", errorCode.getCode(),
                "path", request.getRequestURI()
        );

        ResponseDto<Map<String, Object>> body =
                new ResponseDto<>(status.value(), errorCode.getMsg(), data);

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ResponseDto<Map<String, Object>>> handleAuthenticationException(
            AuthenticationException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.PASSWORD_MISMATCH;
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());

        log.warn("AuthenticationException: path={}, message={}",
                request.getRequestURI(), e.getMessage());

        Map<String, Object> data = Map.of(
                "code", errorCode.getCode(),
                "path", request.getRequestURI()
        );

        ResponseDto<Map<String, Object>> body =
                new ResponseDto<>(status.value(), errorCode.getMsg(), data);

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e, HttpServletRequest req) {
        return ResponseEntity
                .status(ErrorCode.PASSWORD_MISMATCH.getStatus())
                .body(ErrorResponse.of(ErrorCode.PASSWORD_MISMATCH, req.getRequestURI()));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException e, HttpServletRequest req) {
        return ResponseEntity
                .status(ErrorCode.STUDENT_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.STUDENT_NOT_FOUND, req.getRequestURI()));
    }

}
