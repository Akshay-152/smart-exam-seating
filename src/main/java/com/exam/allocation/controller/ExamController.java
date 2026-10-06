package com.exam.allocation.controller;

import com.exam.allocation.model.Exam;
import com.exam.allocation.repository.ExamRepository;
import com.exam.allocation.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** CRUD endpoints for exams (subject, course, semester, date, time, application ID). */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SeatRepository seatRepository;

    @GetMapping
    public List<Exam> getAllExams() {
        List<Exam> exams = examRepository.findAll();
        exams.sort((a, b) -> {
            if (a.getExamDate() != null && b.getExamDate() != null) {
                return a.getExamDate().compareTo(b.getExamDate());
            }
            return Long.compare(a.getId(), b.getId());
        });
        return exams;
    }

    @PostMapping
    public Exam addExam(@RequestBody Exam exam) {
        return examRepository.save(exam);
    }

    @PutMapping("/{id}")
    public Exam updateExam(@PathVariable Long id, @RequestBody Exam exam) {
        Exam existing = examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
        existing.setSubjectName(exam.getSubjectName());
        existing.setCourse(exam.getCourse());
        existing.setSemester(exam.getSemester());
        existing.setExamDate(exam.getExamDate());
        existing.setExamTime(exam.getExamTime());
        existing.setApplicationId(exam.getApplicationId());
        return examRepository.save(existing);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteExam(@PathVariable Long id) {
        if (!examRepository.existsById(id)) {
            throw new IllegalArgumentException("Exam not found with id " + id);
        }
        // Also remove the seating chart belonging to this exam.
        seatRepository.deleteAll(seatRepository.findByExamId(id));
        examRepository.deleteById(id);
        return Map.of("deleted", true);
    }
}
