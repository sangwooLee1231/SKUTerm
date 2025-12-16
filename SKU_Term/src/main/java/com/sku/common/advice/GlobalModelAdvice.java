package com.sku.common.advice;

import com.sku.member.mapper.StudentMapper;
import com.sku.member.vo.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final StudentMapper studentMapper;

    @ModelAttribute
    public void addAttributes(Model model, @AuthenticationPrincipal User user) {
        if (user != null) {
            String studentNumber = user.getUsername();

            Student student = studentMapper.findByStudentNumber(studentNumber);

            if (student != null) {
                String userText = String.format("%s(%s)", student.getName(), student.getStudentNumber());
                model.addAttribute("currentUserText", userText);
                model.addAttribute("currentTermText", "2025-2학기");
            }
        } else {
            model.addAttribute("currentUserText", null);
        }
    }
}