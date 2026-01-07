package com.sku.enrollment.mapper;

import com.sku.enrollment.dto.EnrollmentListResponseDto;
import com.sku.lecture.vo.Lecture;
import com.sku.lecture.vo.LectureTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnrollmentMapper {

    // 이미 신청한 강의인지 확인
    int existsEnrollment(@Param("studentId") Long studentId,
                         @Param("lectureId") Long lectureId);

    // 수강신청 INSERT
    int insertEnrollment(@Param("studentId") Long studentId,
                         @Param("lectureId") Long lectureId);

    // 수강신청 삭제
    int deleteEnrollment(@Param("studentId") Long studentId,
                         @Param("lectureId") Long lectureId);

    // 현재 신청 학점 합계
    int sumCreditsByStudent(@Param("studentId") Long studentId);

    int increaseCurrentCount(@Param("lectureId") Long lectureId);

    // 정원 초과가 아닌 경우에만 원자적으로 +1 (비교군)
    int increaseCurrentCountIfAvailable(@Param("lectureId") Long lectureId);

    // 해당 학생이 신청한 모든 강의의 시간 정보
    List<LectureTime> findEnrolledLectureTimes(@Param("studentId") Long studentId);

    // 수강신청 목록 조회
    List<EnrollmentListResponseDto> findMyEnrollments(@Param("studentId") Long studentId);

    // 정원 증가
    Lecture findLectureWithLock(@Param("lectureId") Long lectureId);
    // 정원 감소
    int decreaseCurrentCount(@Param("lectureId") Long lectureId);
}
