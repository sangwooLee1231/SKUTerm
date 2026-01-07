package com.sku.queue.service.serviceImpl;

import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.queue.dto.QueueJoinResponseDto;
import com.sku.queue.dto.QueueStatusResponseDto;
import com.sku.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${peakguard.queue.active-ttl-seconds:900}")
    private long activeTtlSeconds;

    private static final String QUEUE_COUNTER_KEY = "queue:counter";
    private static final String QUEUE_WAITING_ZSET_KEY = "queue:waiting";
    private static final String QUEUE_TOKEN_PREFIX = "queue:token:";
    private static final String QUEUE_ACTIVE_ZSET_KEY = "queue:active";

    // 초당 입장 허용 인원
    private static final String KEY_THROUGHPUT = "queue:config:throughput";

    @Value("${peakguard.queue.default-throughput:10}")
    private long defaultThroughput;

    @Value("${peakguard.queue.max-active-users:100}")
    private long maxActiveUsers;

    // 최대 활성 사용자 수
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

        long nowMs = nowMs();
        purgeExpiredActive(nowMs);

        Long currentActive = stringRedisTemplate.opsForZSet().zCard(QUEUE_ACTIVE_ZSET_KEY);
        if (currentActive == null) currentActive = 0L;

        boolean isActive = false;
        if (currentActive < maxActiveUsers) {
            stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_ZSET_KEY, token);
            stringRedisTemplate.opsForZSet().add(QUEUE_ACTIVE_ZSET_KEY, token, (double) nextExpireAtMs(nowMs));
            isActive = true;
            log.info("대기열 즉시 입장(FastPass) - token={}", token);
        }

        return new QueueJoinResponseDto(token, queueNumber, position, isActive);
    }

    @Override
    public QueueStatusResponseDto getStatus(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) throw new CustomException(ErrorCode.QUEUE_TOKEN_INVALID);

        //  이미 입장한 상태인지 확인 (Active ZSET score=expireAtMillis)
        long nowMs = nowMs();
        Double expireAt = stringRedisTemplate.opsForZSet().score(QUEUE_ACTIVE_ZSET_KEY, queueToken);
        if (expireAt != null) {
            if (expireAt.longValue() > nowMs) {
                return new QueueStatusResponseDto(queueToken, 0L, 0L, true, 0L);
            }
            // 만료된 토큰 정리
            stringRedisTemplate.opsForZSet().remove(QUEUE_ACTIVE_ZSET_KEY, queueToken);
        }

        //  대기열 순번 조회
        Long rank = stringRedisTemplate.opsForZSet().rank(QUEUE_WAITING_ZSET_KEY, queueToken);
        if (rank == null) {
            throw new CustomException(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }

        //  예상 대기 시간 계산 (내 등수 / 초당 처리량)
        String throughputStr = stringRedisTemplate.opsForValue().get(KEY_THROUGHPUT);
        long throughput = (throughputStr != null) ? Long.parseLong(throughputStr) : defaultThroughput;
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

        long nowMs = nowMs();
        Double expireAt = stringRedisTemplate.opsForZSet().score(QUEUE_ACTIVE_ZSET_KEY, queueToken);

        if (expireAt == null) {
            throw new CustomException(ErrorCode.QUEUE_NOT_ACTIVE);
        }

        // 만료 여부 확인 (만료면 정리 후 차단)
        if (expireAt.longValue() <= nowMs) {
            stringRedisTemplate.opsForZSet().remove(QUEUE_ACTIVE_ZSET_KEY, queueToken);
            throw new CustomException(ErrorCode.QUEUE_NOT_ACTIVE);
        }

        // 슬라이딩 만료: 접근이 있을 때마다 expireAt 갱신
        stringRedisTemplate.opsForZSet().add(QUEUE_ACTIVE_ZSET_KEY, queueToken, (double) nextExpireAtMs(nowMs));
    }

    /**
     *  1초마다 실행되어 대기열 상위 유저를 입장시킴
     */
    @Override
    public void promoteNextBatch() {
        // 현재 입장해있는 유저 수 확인
        long nowMs = nowMs();
        long purged = purgeExpiredActive(nowMs);
        if (purged > 0) {
            log.debug("Purged expired active tokens: {}", purged);
        }

        Long currentActive = stringRedisTemplate.opsForZSet().zCard(QUEUE_ACTIVE_ZSET_KEY);
        if (currentActive == null) currentActive = 0L;

        if (currentActive >= maxActiveUsers) {
            log.debug("Active user full. current={}, max={}", currentActive, maxActiveUsers);
            return;
        }

        // 이번에 입장시킬 인원 수 계산
        String throughputStr = stringRedisTemplate.opsForValue().get(KEY_THROUGHPUT);
        long throughput = (throughputStr != null) ? Long.parseLong(throughputStr) : defaultThroughput;

        // 실제 입장 가능 수 = Min(설정된 처리량, 남은 자리)
        long availableSlots = maxActiveUsers - currentActive;
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
                stringRedisTemplate.opsForZSet().add(QUEUE_ACTIVE_ZSET_KEY, token, (double) nextExpireAtMs(nowMs));
            }
        }

        log.info(" 스케줄러 실행: {}명 입장 승인 (현재 Active: {}명)", targetTokens.size(), currentActive + targetTokens.size());
    }

    

private long nowMs() {
    return System.currentTimeMillis();
}

/**
 * Active 토큰 만료 처리 (누수 방지)
 * - score: expireAt(epochMillis)
 */
private long purgeExpiredActive(long nowMs) {
    // Backward-compat: 이전 구현에서는 queue:active 키 타입이 SET이었음
    // (ZSET로 전환하면서 WRONGTYPE 오류가 날 수 있으므로, 감지 시 1회 마이그레이션)
    DataType type = stringRedisTemplate.type(QUEUE_ACTIVE_ZSET_KEY);
    if (type == DataType.SET) {
        Set<String> legacy = stringRedisTemplate.opsForSet().members(QUEUE_ACTIVE_ZSET_KEY);
        stringRedisTemplate.delete(QUEUE_ACTIVE_ZSET_KEY);

        if (legacy != null && !legacy.isEmpty()) {
            long expireAt = nextExpireAtMs(nowMs);
            for (String token : legacy) {
                if (token != null) {
                    stringRedisTemplate.opsForZSet().add(QUEUE_ACTIVE_ZSET_KEY, token, (double) expireAt);
                }
            }
        }
    }

    Long removed = stringRedisTemplate.opsForZSet()
            .removeRangeByScore(QUEUE_ACTIVE_ZSET_KEY, 0, nowMs);
    return removed == null ? 0L : removed;
}

private long nextExpireAtMs(long nowMs) {
    return nowMs + Duration.ofSeconds(activeTtlSeconds).toMillis();
}

private String generateToken() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void removeToken(String queueToken) {
        if (queueToken != null) {
            stringRedisTemplate.opsForZSet().remove(QUEUE_WAITING_ZSET_KEY, queueToken);
            stringRedisTemplate.opsForZSet().remove(QUEUE_ACTIVE_ZSET_KEY, queueToken);
            stringRedisTemplate.delete(QUEUE_TOKEN_PREFIX + queueToken);

            log.info("대기열 토큰 삭제 완료: {}", queueToken);
        }
    }

    @Override
    public Map<String, Object> resetQueueState() {
        stringRedisTemplate.delete("queue:counter");
        stringRedisTemplate.delete("queue:waiting");
        stringRedisTemplate.delete("queue:active");
        stringRedisTemplate.delete("queue:throughput");

        // queue:token:* 삭제 (로컬이므로 KEYS 사용 OK)
        Set<String> tokenKeys = stringRedisTemplate.keys("queue:token:*");
        int tokenKeyCount = (tokenKeys == null) ? 0 : tokenKeys.size();

        long deletedTokenKeys = 0L;
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            Long deleted = stringRedisTemplate.delete(tokenKeys);
            deletedTokenKeys = (deleted == null) ? 0L : deleted;
        }

        // 3) 응답은 HashMap으로 (Map.of는 null에 취약)
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", true);
        result.put("tokenKeyMatched", tokenKeyCount);
        result.put("tokenKeyDeleted", deletedTokenKeys);
        return result;
    }
}