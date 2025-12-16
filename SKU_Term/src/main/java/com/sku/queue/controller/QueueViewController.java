package com.sku.queue.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/queue")
public class QueueViewController {

    @GetMapping("/waiting")
    public String waitingPage() {
        return "queue";
    }
}