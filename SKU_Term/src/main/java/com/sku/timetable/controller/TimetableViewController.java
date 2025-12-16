package com.sku.timetable.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/timetable")
public class TimetableViewController {

    @GetMapping
    public String timetablePage() {
        return "timetable";
    }
}