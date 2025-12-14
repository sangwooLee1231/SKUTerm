package com.sku.enrollment.controller;

import com.sku.common.dto.ResponseDto;
import com.sku.enrollment.dto.EnrollmentListResponseDto;
import com.sku.enrollment.dto.EnrollmentRequestDto;
import com.sku.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * 수강신청
     */
    @PostMapping
    public ResponseEntity<ResponseDto<Map<String, Object>>> enroll(
            @Valid @RequestBody EnrollmentRequestDto request,
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        enrollmentService.enroll(studentNumber, request.getLectureId());

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "수강신청이 완료되었습니다.",
                        Map.of("lectureId", request.getLectureId())
                )
        );
    }

    /**
     * 수강 취소
     */
    @DeleteMapping("/{lectureId}")
    public ResponseEntity<ResponseDto<Map<String, Object>>> cancel(
            @PathVariable Long lectureId,
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        enrollmentService.cancel(studentNumber, lectureId);

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "수강신청이 취소되었습니다.",
                        Map.of("lectureId", lectureId)
                )
        );
    }

    /**
     * 수강신청 목록 조회
     */
    @GetMapping("/my")
    public ResponseEntity<ResponseDto<Map<String, Object>>> getMyEnrollments(
            @AuthenticationPrincipal User user
    ) {
        String studentNumber = user.getUsername();

        List<EnrollmentListResponseDto> enrollments =
                enrollmentService.getMyEnrollments(studentNumber);

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "나의 수강신청 목록 조회 성공",
                        Map.of("enrollments", enrollments)
                )
        );
    }
}
