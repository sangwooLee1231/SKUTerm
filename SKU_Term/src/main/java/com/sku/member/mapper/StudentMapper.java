package com.sku.member.mapper;

import com.sku.member.vo.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentMapper {
    Student findByStudentNumber(@Param("studentNumber") String studentNumber);

    int existsByStudentNumber(@Param("studentNumber") String studentNumber);

    int insertStudent(Student student);


}