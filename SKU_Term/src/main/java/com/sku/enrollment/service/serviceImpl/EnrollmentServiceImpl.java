package com.sku.enrollment.service.serviceImpl;

import com.sku.common.exception.CustomException;
import com.sku.common.util.ErrorCode;
import com.sku.enrollment.dto.EnrollmentListResponseDto;
import com.sku.enrollment.mapper.EnrollmentMapper;
import com.sku.enrollment.enums.EnrollmentLockMode;
import com.sku.lecture.mapper.LectureMapper;
import com.sku.lecture.vo.Lecture;
import com.sku.lecture.vo.LectureTime;
import com.sku.member.mapper.StudentMapper;
import com.sku.member.vo.Student;
import com.sku.enrollment.service.EnrollmentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Value("${peakguard.enrollment.lock-mode:NONE}")
    private EnrollmentLockMode lockMode;

    private static final int MAX_CREDIT = 20;

    @Value("${peakguard.enrollment.cancel-start-date:2025-03-01}")
    private String cancelStartDateStr;

    @Value("${peakguard.enrollment.cancel-end-date:2025-03-31}")
    private String cancelEndDateStr;

    private LocalDate cancelStartDate;
    private LocalDate cancelEndDate;

    @PostConstruct
    void initCancelDateRange() {
        this.cancelStartDate = LocalDate.parse(cancelStartDateStr);
        this.cancelEndDate = LocalDate.parse(cancelEndDateStr);
    }

    @Override
    public void validateCancelPeriod() {
        LocalDate today = LocalDate.now();
        if (today.isBefore(cancelStartDate) || today.isAfter(cancelEndDate)) {
            throw new CustomException(ErrorCode.CANCEL_PERIOD_EXPIRED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enroll(String studentNumber, Long lectureId) {


        Student student = studentMapper.findByStudentNumber(studentNumber);
        if (student == null) {
            throw new CustomException(ErrorCode.STUDENT_NOT_FOUND);
        }
        Long studentId = student.getId();

        Lecture lecture;
        if (lockMode == EnrollmentLockMode.PESSIMISTIC) {
            lecture = enrollmentMapper.findLectureWithLock(lectureId);
        } else {
            lecture = lectureMapper.findById(lectureId);
        }
        if (lecture == null) {
            throw new CustomException(ErrorCode.LECTURE_NOT_FOUND);
        }

        int exists = enrollmentMapper.existsEnrollment(studentId, lectureId);
        if (exists > 0) {
            throw new CustomException(ErrorCode.ALREADY_ENROLLED);
        }

        int currentCredits = enrollmentMapper.sumCreditsByStudent(studentId);
        int totalAfterEnroll = currentCredits + (lecture.getCredit() != null ? lecture.getCredit() : 0);
        if (totalAfterEnroll > MAX_CREDIT) {
            throw new CustomException(ErrorCode.CREDIT_EXCEEDED);
        }

        if (lockMode != EnrollmentLockMode.ATOMIC_UPDATE) {
            if (lecture.getCurrentCount() >= lecture.getMaxCapacity()) {
                throw new CustomException(ErrorCode.ENROLLMENT_CAPACITY_FULL);
            }
        }

        List<LectureTime> enrolledTimes = enrollmentMapper.findEnrolledLectureTimes(studentId);
        List<LectureTime> newLectureTimes = lectureMapper.findTimesByLectureId(lectureId);
        if (hasTimeConflict(enrolledTimes, newLectureTimes)) {
            throw new CustomException(ErrorCode.TIME_CONFLICT);
        }

        int inc;
        if (lockMode == EnrollmentLockMode.ATOMIC_UPDATE) {
            inc = enrollmentMapper.increaseCurrentCountIfAvailable(lectureId);
            if (inc == 0) {
                throw new CustomException(ErrorCode.ENROLLMENT_CAPACITY_FULL);
            }
        } else {
            inc = enrollmentMapper.increaseCurrentCount(lectureId);
        }


        try {
            int inserted = enrollmentMapper.insertEnrollment(studentId, lectureId);
            if (inserted == 0) {
                throw new CustomException(ErrorCode.ENROLLMENT_FAILED);
            }
        } catch (DataIntegrityViolationException e) {
            // 유니크 인덱스 충돌 → 이미 신청한 강의
            throw new CustomException(ErrorCode.ALREADY_ENROLLED);
        }

        log.info("수강신청 완료(lockMode={}) - studentId={}, lectureId={}", lockMode, studentId, lectureId);
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
