package com.sku.enrollment.vo;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Enrollment {

    private Long id;           // ENROLLMENT_ID
    private Long studentId;    // ENROLLMENT_STUDENT_ID
    private Long lectureId;    // ENROLLMENT_LECTURE_ID
    private LocalDateTime createdAt; // ENROLLMENT_CREATED_AT
}