package com.sku.admin.controller;

import com.sku.common.dto.ResponseDto;
import com.sku.enrollment.service.EnrollmentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminEnrollmentController {

    private final EnrollmentAdminService enrollmentAdminService;

    @PostMapping("/lectures/recalculate-current-counts")
    public ResponseEntity<ResponseDto<Map<String, Object>>> recalculateLectureCurrentCounts() {

        int affected = enrollmentAdminService.recalculateLectureCurrentCounts();

        return ResponseEntity.ok(
                new ResponseDto<>(
                        HttpStatus.OK.value(),
                        "lecture_current_count 재계산 완료",
                        Map.of("affectedLectures", affected)
                )
        );
    }
}
