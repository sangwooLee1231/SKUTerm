package com.sku.queue.scheduler;

import com.sku.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;

    // 1초 마다 실행
    @Scheduled(fixedDelay = 1000)
    public void queueProcess() {
        try {
            queueService.promoteNextBatch();
        } catch (Exception e) {
            log.error("대기열 스케줄러 오류 발생", e);
        }
    }
}