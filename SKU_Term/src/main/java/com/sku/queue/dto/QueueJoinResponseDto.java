package com.sku.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 대기열 진입 시 사용자에게 내려줄 정보 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueueJoinResponseDto {

    private String queueToken;   // 대기열 토큰
    private Long queueNumber;    // 발급된 대기 번호
    private Long position;       // 현재 대기열 내 내 위치
    private boolean active;
}
