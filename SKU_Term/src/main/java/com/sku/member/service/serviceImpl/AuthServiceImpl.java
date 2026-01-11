package com.sku.member.service.serviceImpl;

import com.sku.common.exception.CustomException;
import com.sku.common.jwt.JwtTokenProvider;
import com.sku.common.util.ErrorCode;
import com.sku.member.mapper.StudentMapper;
import com.sku.member.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final StudentMapper studentMapper;

    @Override
    public Map<String, String> login(String studentNumber, String password) {
        int exists = studentMapper.existsByStudentNumber(studentNumber);

        if (exists == 0) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(studentNumber, password)
            );
        } catch (BadCredentialsException e) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // ID/PW 인증 요청
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(studentNumber, password);

        //  검증
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        // 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        // Redis 저장 (Key: studentNumber)
        redisTemplate.opsForValue().set(
                studentNumber, // key를 학번으로 저장
                refreshToken,
                6,
                TimeUnit.HOURS
        );

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        tokens.put("refreshToken", refreshToken);
        return tokens;
    }

    @Override
    public Map<String, String> reissue(String refreshToken) {

        // RefreshToken null 체크
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }

        // RefreshToken 유효성 검증 (서명/만료)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.SESSION_EXPIRED);
        }

        // RefreshToken으로부터 사용자 정보 추출
        Authentication authentication = jwtTokenProvider.getAuthentication(refreshToken);
        String studentNumber = authentication.getName();

        // Redis에 저장된 RefreshToken 조회
        String storedRefreshToken = redisTemplate.opsForValue().get(studentNumber);

        // Redis에 없거나, 값이 다르면 유효하지 않은(혹은 탈취된) 토큰
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.SESSION_EXPIRED);
        }

        // 새로운 Access/Refresh 토큰 발급 (Refresh Rotation)
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);

        // Redis 갱신
        redisTemplate.opsForValue().set(
                studentNumber,
                newRefreshToken,
                6,
                TimeUnit.HOURS
        );

        // 반환
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        return tokens;
    }

    @Override
    public void logout(String studentNumber) {
        redisTemplate.delete(studentNumber);
        log.info("Logout - refreshToken removed for studentNumber={}", studentNumber);
    }
}