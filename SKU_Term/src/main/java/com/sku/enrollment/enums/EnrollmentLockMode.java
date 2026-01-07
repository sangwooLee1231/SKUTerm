package com.sku.enrollment.enums;

/**
 * 수강신청 동시성 제어 전략 비교를 위한 Lock Mode
 */
public enum EnrollmentLockMode {
    /**
     * SELECT ... FOR UPDATE 기반 비관적 락
     */
    PESSIMISTIC,

    /**
     * 락 없이 조회 후 정원 체크 + 증가 (비교군: 오버부킹 가능)
     */
    NONE,

    /**
     * UPDATE ... WHERE current_count < max_capacity 형태의 원자적 증가
     */
    ATOMIC_UPDATE
}
