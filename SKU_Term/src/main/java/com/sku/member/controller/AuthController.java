package com.sku.member.controller;

import com.sku.common.dto.ResponseDto;
import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.member.dto.MemberLoginRequestDto;
import com.sku.member.dto.MemberSignUpRequestDto;
import com.sku.member.service.AuthService;
import com.sku.member.service.MemberService;
import com.sku.queue.service.QueueService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;
    private final QueueService queueService;

    @PostMapping("/signup")
    public ResponseEntity<ResponseDto<Map<String, Object>>> signUp(
            @Valid @RequestBody MemberSignUpRequestDto requestDto
    ) {
        Long studentId = memberService.signUp(requestDto);


        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "회원가입 성공", Map.of("student_id", studentId, "student_number", requestDto.getStudentNumber())));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody MemberLoginRequestDto request, HttpServletResponse response) {
        // 서비스 로그인 로직 수행
        Map<String, String> tokens = authService.login(request.getStudentNumber(), request.getPassword());

        // Access Token 쿠키 생성 (1시간)
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
                .httpOnly(true)          // JavaScript에서 접근 불가 (XSS 방지)
                .secure(false)           // HTTP에서도 전송 가능 (HTTPS 적용 시 true로 변경 필요)
                .path("/")               // 모든 경로에서 쿠키 전송
                .maxAge(60 * 60)         // 1시간 (3600초)
                .sameSite("Strict")      // CSRF 방지 강화
                .build();

        // Refresh Token 쿠키 생성 (6시간)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.get("refreshToken"))
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(60 * 60 * 6)
                .sameSite("Strict")
                .build();

        // 응답 헤더에 쿠키 추가
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "로그인에 성공했습니다.",
                        Map.of("accessToken", tokens.get("accessToken"))
                )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 쿠키에서 refreshToken 추출
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }

        // 서비스에 재발급 요청
        Map<String, String> tokens = authService.reissue(refreshToken);

        // 새 Access/Refresh 토큰을 쿠키로 재설정
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.get("accessToken"))
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(60 * 60)
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.get("refreshToken"))
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(60 * 60 * 6)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "토큰 재발급 성공.",
                        Map.of("accessToken", tokens.get("accessToken"))
                )
        );
    }


    @PostMapping("/logout")
    public ResponseEntity<ResponseDto<Void>> logout(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String studentNumber = user.getUsername();
        authService.logout(studentNumber);

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("queueToken".equals(cookie.getName())) {
                    String queueToken = cookie.getValue();
                    queueService.removeToken(queueToken);
                    break;
                }
            }
        }
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).sameSite("Strict").build();
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(false).path("/").maxAge(0).sameSite("Strict").build();

        ResponseCookie queueCookie = ResponseCookie.from("queueToken", "")
                .path("/").maxAge(0).build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, queueCookie.toString());

        return ResponseEntity.ok(
                new ResponseDto<>(HttpStatus.OK.value(), "로그아웃에 성공했습니다.", null)
        );
    }
}