package com.sku.queue.service.serviceImpl;

import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.queue.dto.QueueJoinResponseDto;
import com.sku.queue.dto.QueueStatusResponseDto;
import com.sku.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String QUEUE_COUNTER_KEY = "queue:counter";
    private static final String QUEUE_WAITING_ZSET_KEY = "queue:waiting";
    private static final String QUEUE_TOKEN_PREFIX = "queue:token:";
    private static final String QUEUE_ACTIVE_SET_KEY = "queue:active";

    // 초당 입장 허용 인원 (Redis로 관리 가능)
    private static final String KEY_THROUGHPUT = "queue:config:throughput";
    private static final long DEFAULT_THROUGHPUT = 10L;

    // 최대 활성 사용자 수 (DB 보호용 절대 상한선)
    private static final long MAX_ACTIVE_USERS = 1;

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    @Override
    public QueueJoinResponseDto joinQueue() {
        String token = generateToken();
        Long queueNumber = stringRedisTemplate.opsForValue().increment(QUEUE_COUNTER_KEY);

        String tokenKey = QUEUE_TOKEN_PREFIX + token;
        stringRedisTemplate.opsForValue().set(tokenKey, String.valueOf(queueNumber), TOKEN_TTL);
        stringRedisTemplate.opsForZSet().add(QUEUE_WAITING_ZSET_KEY, token, queueNumber.doubleValue());

        Long rank = stringRedisTemplate.opsForZSet().rank(QUEUE_WAITING_ZSET_KEY, token);
        long position = (rank == null ? queueNumber : rank + 1);

        Long currentActive = stringRedisTemplate.opsForSet().size(QUEUE_ACTIVE_SET_KEY);
        if (currentActive == null) currentActive = 0L;

        boolean isActive = false;
        if (currentActive < MAX_ACTIVE_USERS) {
            stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_ZSET_KEY, token);
            stringRedisTemplate.opsForSet().add(QUEUE_ACTIVE_SET_KEY, token);
            isActive = true;
            log.info("대기열 즉시 입장(FastPass) - token={}", token);
        }

        return new QueueJoinResponseDto(token, queueNumber, position, isActive);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueStatusResponseDto getStatus(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) throw new CustomException(ErrorCode.QUEUE_TOKEN_INVALID);

        //  이미 입장한 상태인지 확인
        Boolean isActive = stringRedisTemplate.opsForSet().isMember(QUEUE_ACTIVE_SET_KEY, queueToken);
        if (Boolean.TRUE.equals(isActive)) {
            return new QueueStatusResponseDto(queueToken, 0L, 0L, true, 0L);
        }

        //  대기열 순번 조회
        Long rank = stringRedisTemplate.opsForZSet().rank(QUEUE_WAITING_ZSET_KEY, queueToken);
        if (rank == null) {
            throw new CustomException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }

        //  예상 대기 시간 계산 (내 등수 / 초당 처리량)
        String throughputStr = stringRedisTemplate.opsForValue().get(KEY_THROUGHPUT);
        long throughput = (throughputStr != null) ? Long.parseLong(throughputStr) : DEFAULT_THROUGHPUT;
        if (throughput <= 0) throughput = 1;

        long position = rank + 1;
        long estimatedSeconds = (position + throughput - 1) / throughput;

        return new QueueStatusResponseDto(queueToken, null, position, false, estimatedSeconds);
    }

    @Override
    public void validateActiveToken(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new CustomException(ErrorCode.QUEUE_TOKEN_INVALID);
        }

        Boolean isActive = stringRedisTemplate.opsForSet().isMember(QUEUE_ACTIVE_SET_KEY, queueToken);
        if (!Boolean.TRUE.equals(isActive)) {
            throw new CustomException(ErrorCode.QUEUE_NOT_ACTIVE);
        }
    }

    /**
     *  1초마다 실행되어 대기열 상위 유저를 입장시킴
     */
    @Override
    public void promoteNextBatch() {
        // 현재 입장해있는 유저 수 확인
        Long currentActive = stringRedisTemplate.opsForSet().size(QUEUE_ACTIVE_SET_KEY);
        if (currentActive == null) currentActive = 0L;

        if (currentActive >= MAX_ACTIVE_USERS) {
            log.debug("Active user full. current={}, max={}", currentActive, MAX_ACTIVE_USERS);
            return;
        }

        // 이번에 입장시킬 인원 수 계산
        String throughputStr = stringRedisTemplate.opsForValue().get(KEY_THROUGHPUT);
        long throughput = (throughputStr != null) ? Long.parseLong(throughputStr) : DEFAULT_THROUGHPUT;

        // 실제 입장 가능 수 = Min(설정된 처리량, 남은 자리)
        long availableSlots = MAX_ACTIVE_USERS - currentActive;
        long promoteCount = Math.min(throughput, availableSlots);

        if (promoteCount <= 0) return;

        // 대기열(ZSet)에서 상위 N명 꺼내기 (Pop Min)
        Set<ZSetOperations.TypedTuple<String>> targetTokens =
                stringRedisTemplate.opsForZSet().popMin(QUEUE_WAITING_ZSET_KEY, promoteCount);

        if (targetTokens == null || targetTokens.isEmpty()) return;

        // Active Set으로 이동 (입장 처리)
        for (ZSetOperations.TypedTuple<String> tuple : targetTokens) {
            String token = tuple.getValue();
            if (token != null) {
                stringRedisTemplate.opsForSet().add(QUEUE_ACTIVE_SET_KEY, token);
            }
        }

        log.info(" 스케줄러 실행: {}명 입장 승인 (현재 Active: {}명)", targetTokens.size(), currentActive + targetTokens.size());
    }

    private String generateToken() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void removeToken(String queueToken) {
        if (queueToken != null) {
            stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_ZSET_KEY, queueToken);
            stringRedisTemplate.opsForSet().remove(QUEUE_ACTIVE_SET_KEY, queueToken);
            stringRedisTemplate.delete(QUEUE_TOKEN_PREFIX + queueToken);

            log.info("대기열 토큰 삭제 완료: {}", queueToken);
        }
    }
}