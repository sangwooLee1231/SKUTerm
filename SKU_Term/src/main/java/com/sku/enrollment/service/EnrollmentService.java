package com.sku.enrollment.service;

import com.sku.enrollment.dto.EnrollmentListResponseDto;

import java.util.List;

public interface EnrollmentService {

    void validateCancelPeriod();

    // 수강신청
    void enroll(String studentNumber, Long lectureId);

    // 수강 취소
    void cancel(String studentNumber, Long lectureId);

    // 수강신청 목록 조회
    List<EnrollmentListResponseDto> getMyEnrollments(String studentNumber);
}
