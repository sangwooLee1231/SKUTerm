package com.sku.queue.service;

import com.sku.queue.dto.QueueJoinResponseDto;
import com.sku.queue.dto.QueueStatusResponseDto;

import java.util.Map;

public interface QueueService {

    // 대기열 진입
    QueueJoinResponseDto joinQueue();
    // 대기열 상태 조회
    QueueStatusResponseDto getStatus(String queueToken);

    void validateActiveToken(String queueToken);
    void promoteNextBatch();
    void removeToken(String queueToken);
    Map<String, Object> resetQueueState();

}
