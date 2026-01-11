-- PR-12: DB 제약조건 명문화 (MySQL)
-- 목적: 코드 전제(중복 신청 방지/참조 무결성/카운터 범위)를 DB 레벨로 고정
--
-- 적용 대상 테이블(현재 프로젝트 기준): Student, Lecture, Enrollment, Lecturetime
--
-- 주의:
-- 1) 이미 데이터가 있는 DB에 적용할 때, 중복/무결성 위반 데이터가 있으면 migration이 실패할 수 있습니다.
-- 2) Flyway는 1회 실행 후 flyway_schema_history에 기록되며, 이후에는 재실행되지 않습니다.

-- -------------------------------------------------------------------------------------------------
-- 0) Enrollment 중복 데이터 제거(같은 학생-같은 강의가 여러 번 존재하는 경우)
--    (가장 작은 ENROLLMENT_ID만 남기고 나머지 삭제)
-- -------------------------------------------------------------------------------------------------
DELETE e1
FROM Enrollment e1
JOIN Enrollment e2
  ON e1.ENROLLMENT_STUDENT_ID = e2.ENROLLMENT_STUDENT_ID
 AND e1.ENROLLMENT_LECTURE_ID = e2.ENROLLMENT_LECTURE_ID
 AND e1.ENROLLMENT_ID > e2.ENROLLMENT_ID;

-- -------------------------------------------------------------------------------------------------
-- 1) Student: STUDENT_NUMBER 유니크(로그인 ID 중복 방지)
-- -------------------------------------------------------------------------------------------------
SET @has_uk_student_number := (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'Student'
      AND constraint_name = 'UK_student_number'
);

SET @sql := IF(
    @has_uk_student_number = 0,
    'ALTER TABLE Student ADD CONSTRAINT UK_student_number UNIQUE (STUDENT_NUMBER)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------------------
-- 2) Enrollment: FK를 위한 인덱스(lecture_id는 단독 인덱스가 필요)
-- -------------------------------------------------------------------------------------------------
SET @has_ix_enrollment_lecture := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'Enrollment'
      AND index_name = 'IX_enrollment_lecture_id'
);

SET @sql := IF(
    @has_ix_enrollment_lecture = 0,
    'CREATE INDEX IX_enrollment_lecture_id ON Enrollment (ENROLLMENT_LECTURE_ID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------------------
-- 3) Enrollment: (student_id, lecture_id) UNIQUE
-- -------------------------------------------------------------------------------------------------
SET @has_uk_enrollment_student_lecture := (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'Enrollment'
      AND constraint_name = 'UK_enrollment_student_lecture'
);

SET @sql := IF(
    @has_uk_enrollment_student_lecture = 0,
    'ALTER TABLE Enrollment ADD CONSTRAINT UK_enrollment_student_lecture UNIQUE (ENROLLMENT_STUDENT_ID, ENROLLMENT_LECTURE_ID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------------------
-- 4) Enrollment: FK(Student/Lecture)
-- -------------------------------------------------------------------------------------------------
SET @has_fk_enrollment_student := (
    SELECT COUNT(*)
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name = 'FK_enrollment_student'
);

SET @sql := IF(
    @has_fk_enrollment_student = 0,
    'ALTER TABLE Enrollment ADD CONSTRAINT FK_enrollment_student FOREIGN KEY (ENROLLMENT_STUDENT_ID) REFERENCES Student (STUDENT_ID) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_enrollment_lecture := (
    SELECT COUNT(*)
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name = 'FK_enrollment_lecture'
);

SET @sql := IF(
    @has_fk_enrollment_lecture = 0,
    'ALTER TABLE Enrollment ADD CONSTRAINT FK_enrollment_lecture FOREIGN KEY (ENROLLMENT_LECTURE_ID) REFERENCES Lecture (LECTURE_ID) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------------------
-- 5) Lecturetime: FK(Lecture)
-- -------------------------------------------------------------------------------------------------
SET @has_ix_lecturetime_lecture := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'Lecturetime'
      AND index_name = 'IX_lecturetime_lecture_id'
);

SET @sql := IF(
    @has_ix_lecturetime_lecture = 0,
    'CREATE INDEX IX_lecturetime_lecture_id ON Lecturetime (LECTURETIME_LECTURE_ID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_fk_lecturetime_lecture := (
    SELECT COUNT(*)
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name = 'FK_lecturetime_lecture'
);

SET @sql := IF(
    @has_fk_lecturetime_lecture = 0,
    'ALTER TABLE Lecturetime ADD CONSTRAINT FK_lecturetime_lecture FOREIGN KEY (LECTURETIME_LECTURE_ID) REFERENCES Lecture (LECTURE_ID) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------------------
-- 6) (선택) Lecture: current_count 범위 CHECK
--    MySQL 8.0+ 에서 CHECK가 실제로 동작합니다.
-- -------------------------------------------------------------------------------------------------
SET @has_ck_lecture_current_count := (
    SELECT COUNT(*)
    FROM information_schema.check_constraints
    WHERE constraint_schema = DATABASE()
      AND constraint_name = 'CK_lecture_current_count'
);

SET @sql := IF(
    @has_ck_lecture_current_count = 0,
    'ALTER TABLE Lecture ADD CONSTRAINT CK_lecture_current_count CHECK (LECTURE_CURRENT_COUNT >= 0 AND LECTURE_CURRENT_COUNT <= LECTURE_MAX_CAPACITY)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
