package com.sku.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EnrollmentRequestDto {

    @NotNull(message = "강의 ID는 필수 값입니다.")
    private Long lectureId;
}