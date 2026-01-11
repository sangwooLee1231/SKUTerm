package com.sku.member.controller;

import com.sku.common.dto.ResponseDto;
import com.sku.common.exception.CustomException;
import com.sku.common.util.CookieFactory;
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
import org.apache.catalina.User;
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
    private final CookieFactory cookieFactory;


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
    public ResponseEntity<?> login(@Valid @RequestBody MemberLoginRequestDto request,
                                   HttpServletResponse response) {

        Map<String, String> tokens = authService.login(request.getStudentNumber(), request.getPassword());

        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.accessToken(tokens.get("accessToken")).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(tokens.get("refreshToken")).toString());

        return ResponseEntity.ok(new ResponseDto<>(HttpStatus.OK.value(), "로그인에 성공했습니다.", null));
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

        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.accessToken(tokens.get("accessToken")).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(tokens.get("refreshToken")).toString());

        return ResponseEntity.ok(new ResponseDto<>(HttpStatus.OK.value(), "토큰 재발급 성공.", null));
    }


    @PostMapping("/logout")
    public ResponseEntity<ResponseDto<Void>> logout(@AuthenticationPrincipal User user,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {

        // (PR-01에서 이미 처리했겠지만) 안전장치
        if (user == null) throw new CustomException(ErrorCode.LOGIN_REQUIRED);

        authService.logout(user.getUsername());

        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessToken().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshToken().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearQueueToken().toString());

        return ResponseEntity.ok(new ResponseDto<>(HttpStatus.OK.value(), "로그아웃에 성공했습니다.", null));
    }
}