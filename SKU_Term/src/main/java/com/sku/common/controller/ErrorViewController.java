package com.sku.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorViewController {

    @GetMapping("/login-required")
    public String loginRequiredPage() {
        return "error/login_required";
    }
}