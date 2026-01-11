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
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";


    @Override
    public String refreshKey(String studentNumber) {
        return REFRESH_KEY_PREFIX + studentNumber;
    }

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

        String key = refreshKey(studentNumber);
        redisTemplate.delete(studentNumber);
        redisTemplate.opsForValue().set(key, refreshToken, 6, TimeUnit.HOURS);

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
        String key = refreshKey(studentNumber);

        String storedRefreshToken = redisTemplate.opsForValue().get(key);
        if (storedRefreshToken == null) {
            storedRefreshToken = redisTemplate.opsForValue().get(studentNumber);
        }

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.SESSION_EXPIRED);
        }

        // 새로운 Access/Refresh 토큰 발급 (Refresh Rotation)
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);



        redisTemplate.delete(studentNumber);
        redisTemplate.opsForValue().set(key, newRefreshToken, 6, TimeUnit.HOURS);

        // 반환
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);
        return tokens;
    }

    @Override
    public void logout(String studentNumber) {
        redisTemplate.delete(refreshKey(studentNumber));
        redisTemplate.delete(studentNumber);
        log.info("Logout - refreshToken removed for studentNumber={}", studentNumber);
    }
}