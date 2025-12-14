package com.sku.enrollment.service.serviceImpl;

import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.enrollment.dto.EnrollmentListResponseDto;
import com.sku.enrollment.mapper.EnrollmentMapper;
import com.sku.lecture.mapper.LectureMapper;
import com.sku.lecture.vo.Lecture;
import com.sku.lecture.vo.LectureTime;
import com.sku.member.mapper.StudentMapper;
import com.sku.member.vo.Student;
import com.sku.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final LectureMapper lectureMapper;
    private final StudentMapper studentMapper;

    private static final int MAX_CREDIT = 20;

    // 수강 취소 가능 기간
    private static final LocalDate ENROLL_START_DATE = LocalDate.of(2025, 3, 1);
    private static final LocalDate ENROLL_END_DATE   = LocalDate.of(2025, 3, 31);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enroll(String studentNumber, Long lectureId) {

        if (lectureId == null || lectureId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1학생 조회
        Student student = studentMapper.findByStudentNumber(studentNumber);
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        Long studentId = student.getId();

        // 2강의 조회
        Lecture lecture = lectureMapper.findById(lectureId);
        if (lecture == null) {
            throw new CustomException(ErrorCode.LECTURE_NOT_FOUND);
        }

        // 3이미 신청한 강의인지 확인
        int exists = enrollmentMapper.existsEnrollment(studentId, lectureId);
        if (exists > 0) {
            throw new CustomException(ErrorCode.ALREADY_ENROLLED);
        }

        // 4최대 학점 체크
        int currentCredits = enrollmentMapper.sumCreditsByStudent(studentId);
        int totalAfterEnroll = currentCredits + (lecture.getCredit() != null ? lecture.getCredit() : 0);
        if (totalAfterEnroll > MAX_CREDIT) {
            throw new CustomException(ErrorCode.CREDIT_EXCEEDED);
        }

        // 5시간표 중복 체크
        List<LectureTime> enrolledTimes = enrollmentMapper.findEnrolledLectureTimes(studentId);
        List<LectureTime> newLectureTimes = lectureMapper.findTimesByLectureId(lectureId);

        if (hasTimeConflict(enrolledTimes, newLectureTimes)) {
            throw new CustomException(ErrorCode.TIME_CONFLICT);
        }

        // 정원 체크 (실시간)
        int updatedRows = enrollmentMapper.increaseCurrentCountIfNotFull(lectureId);
        if (updatedRows == 0) {
            throw new CustomException(ErrorCode.ENROLLMENT_CAPACITY_FULL);
        }

        int inserted = enrollmentMapper.insertEnrollment(studentId, lectureId);
        if (inserted == 0) {
            throw new CustomException(ErrorCode.ENROLLMENT_FAILED);
        }

        log.info("수강신청 완료 - studentId={}, lectureId={}", studentId, lectureId);
    }

    /**
     * - 수강신청 내역 존재 여부
     * - 수강 취소 기간 체크
     * - 삭제 + 정원 감소
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String studentNumber, Long lectureId) {

        if (lectureId == null || lectureId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        //  학생 조회
        Student student = studentMapper.findByStudentNumber(studentNumber);
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        Long studentId = student.getId();

        //  취소 가능 기간 체크
        validateCancelPeriod();

        //  해당 수강신청 존재 여부
        int exists = enrollmentMapper.existsEnrollment(studentId, lectureId);
        if (exists == 0) {
            throw new CustomException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        int deleted = enrollmentMapper.deleteEnrollment(studentId, lectureId);
        if (deleted == 0) {
            throw new CustomException(ErrorCode.ENROLLMENT_FAILED);
        }

        // 정원 감소
        int dec = enrollmentMapper.decreaseCurrentCount(lectureId);
        if (dec == 0) {
            log.warn("강의 정원 감소에 실패했습니다. lectureId={}", lectureId);
        }

        log.info("수강취소 완료 - studentId={}, lectureId={}", studentId, lectureId);
    }

    /**
     * 수강신청 목록 조회
     */
    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentListResponseDto> getMyEnrollments(String studentNumber) {

        Student student = studentMapper.findByStudentNumber(studentNumber);
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        Long studentId = student.getId();

        List<EnrollmentListResponseDto> list = enrollmentMapper.findMyEnrollments(studentId);
        return list;
    }

    /**
     * 수강 취소 가능 기간 검증
     */
    private void validateCancelPeriod() {
        LocalDate today = LocalDate.now();
        if (today.isBefore(ENROLL_START_DATE) || today.isAfter(ENROLL_END_DATE)) {
            throw new CustomException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }
    }

    /**
     *  시간표 중복 체크
     */
    private boolean hasTimeConflict(List<LectureTime> existing, List<LectureTime> target) {
        if (existing == null || existing.isEmpty() || target == null || target.isEmpty()) {
            return false;
        }

        for (LectureTime newTime : target) {
            for (LectureTime ex : existing) {
                if (!safeEqualsIgnoreCase(newTime.getDayOfWeek(), ex.getDayOfWeek())) {
                    continue;
                }

                // 시간 겹치는지
                if (newTime.getStartTime() != null && newTime.getEndTime() != null
                        && ex.getStartTime() != null && ex.getEndTime() != null) {

                    boolean overlap =
                            newTime.getStartTime().isBefore(ex.getEndTime()) &&
                                    ex.getStartTime().isBefore(newTime.getEndTime());

                    if (overlap) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean safeEqualsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}
