package com.sku.enrollment.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/enrollments")
public class EnrollmentViewController {

    @GetMapping
    public String enrollmentPage() {
        return "enrollments";
    }
}