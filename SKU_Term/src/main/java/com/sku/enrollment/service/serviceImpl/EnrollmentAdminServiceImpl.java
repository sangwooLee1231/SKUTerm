package com.sku.enrollment.service.serviceImpl;

import com.sku.enrollment.mapper.EnrollmentMapper;
import com.sku.enrollment.service.EnrollmentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentAdminServiceImpl implements EnrollmentAdminService {

    private final EnrollmentMapper enrollmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recalculateLectureCurrentCounts() {
        return enrollmentMapper.recalculateLectureCurrentCounts();
    }
}
