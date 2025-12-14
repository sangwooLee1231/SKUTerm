package com.sku.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentListResponseDto {

    private Long enrollmentId;
    private Long lectureId;
    private String courseName;      // 강의명
    private String professor;       // 교수명
    private Integer credit;         // 학점
    private String division;        // 이수구분
    private String room;            // 강의실
    private LocalDateTime createdAt; // 수강신청 시간
}
