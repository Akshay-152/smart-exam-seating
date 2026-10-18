package com.exam.allocation.controller;

import com.exam.allocation.model.Exam;
import com.exam.allocation.model.ExamEligibility;
import com.exam.allocation.model.SeatAllocation;
import com.exam.allocation.repository.ExamEligibilityRepository;
import com.exam.allocation.repository.ExamRepository;
import com.exam.allocation.repository.SeatAllocationRepository;
import com.exam.allocation.repository.SeatRepository;
import com.exam.allocation.service.AuditService;
import com.exam.allocation.service.ConflictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exam management endpoints: CRUD plus the publish/cancel lifecycle, eligibility
 * assignment and scheduling-conflict detection. Administrator role required.
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    @Autowired private ExamRepository examRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatAllocationRepository seatAllocationRepository;
    @Autowired private ExamEligibilityRepository eligibilityRepository;
    @Autowired private ConflictService conflictService;
    @Autowired private AuditService auditService;

    /** All scheduling conflicts in the current exam schedule (same date+time+audience). */
    @GetMapping("/conflicts")
    public List<ConflictService.Conflict> getConflicts() {
        return conflictService.findConflicts();
    }

    @GetMapping
    public List<Exam> getAllExams(@RequestParam(required = false) String status) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        List<Exam> exams = examRepository.findAll();
        if (status != null && !status.isBlank()) {
            Exam.ExamStatus wanted = Exam.ExamStatus.valueOf(status.toUpperCase());
            exams = exams.stream().filter(e -> e.getStatus() == wanted).toList();
        }
        exams.sort((a, b) -> {
            if (a.getExamDate() != null && b.getExamDate() != null) {
                return a.getExamDate().compareTo(b.getExamDate());
            }
            return Long.compare(a.getId(), b.getId());
        });
        return exams;
    }

    @GetMapping("/{id}")
    public Exam getExam(@PathVariable Long id) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        return examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
    }

    @PostMapping
    public Exam addExam(@RequestBody Exam exam) {
        return examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
    }

    @PostMapping
    public Exam addExam(@RequestBody Exam exam) {
        if (exam.getStatus() == null) exam.setStatus(Exam.ExamStatus.DRAFT);
        Exam saved = examRepository.save(exam);
        auditService.record(actor(), "EXAM_CREATED", "exam:" + saved.getId(), label(saved));
        return saved;
    }

    @PutMapping("/{id}")
    public Exam updateExam(@PathVariable Long id, @RequestBody Exam exam) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        Exam existing = examRepository.findById(id)
        Exam existing = examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
        existing.setSubjectName(exam.getSubjectName());
        existing.setSubjectCode(exam.getSubjectCode());
        existing.setBranch(exam.getBranch());
        existing.setCourse(exam.getCourse());
        existing.setExamDate(exam.getExamDate());
        existing.setExamTime(exam.getExamTime());
        existing.setEndTime(exam.getEndTime());
        existing.setSemester(exam.getSemester());
        existing.setExamType(exam.getExamType());
        existing.setInstructions(exam.getInstructions());
        if (exam.getDurationMinutes() != null) existing.setDurationMinutes(exam.getDurationMinutes());
        existing.setApplicationId(exam.getApplicationId());
        // A published/cancelled exam that gets edited returns to draft so nobody
        // sees half-updated information: the administrator re-publishes explicitly.
        if (existing.getStatus() == Exam.ExamStatus.PUBLISHED
                && (exam.getExamDate() != null || exam.getExamTime() != null)) {
            existing.setStatus(Exam.ExamStatus.DRAFT);
        }
        Exam saved = examRepository.save(existing);
        auditService.record(actor(), "EXAM_UPDATED", "exam:" + id, label(saved));
        return saved;
    }

    /** Lifecycle: DRAFT -> PUBLISHED -> COMPLETED, with CANCELLED reachable from any state. */
    @PostMapping("/{id}/status")
    public Exam setExamStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        Exam exam = examRepository.findById(id)
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
        String wanted = body.getOrDefault("status", "").toUpperCase();
        Exam.ExamStatus next;
        try {
            next = Exam.ExamStatus.valueOf(wanted);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown status \"" + wanted + "\". Use DRAFT, PUBLISHED, COMPLETED or CANCELLED.");
        }
        if (next == Exam.ExamStatus.PUBLISHED && (exam.getExamDate() == null || exam.getExamTime() == null)) {
            throw new IllegalArgumentException("Set an exam date and start time before publishing.");
        }
        exam.setStatus(next);
        examRepository.save(exam);
        if (next == Exam.ExamStatus.CANCELLED) {
            // Cancelling hides the seating from students as well.
            List<SeatAllocation> allocations = seatAllocationRepository.findByExamIdAndStatus(id, SeatAllocation.Status.PUBLISHED);
            allocations.forEach(a -> a.setStatus(SeatAllocation.Status.DRAFT));
            seatAllocationRepository.saveAll(allocations);
        }
        auditService.record(actor(), "EXAM_" + next, "exam:" + id, label(exam));
        return exam;
    }

    /** Replaces the batches this exam is published to (empty list = "all students"). */
    @PutMapping("/{id}/eligibility")
    public List<String> setEligibility(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
        List<String> batches = body.getOrDefault("batches", new ArrayList<>());
        eligibilityRepository.deleteByExamId(id);
        List<String> saved = new ArrayList<>();
        for (String batch : batches) {
            if (batch == null || batch.isBlank()) continue;
            String name = batch.trim();
            eligibilityRepository.save(new ExamEligibility(exam, name));
            saved.add(name);
        }
        auditService.record(actor(), "EXAM_ELIGIBILITY", "exam:" + id, "Batches: " + saved);
        return saved;
    }

    @GetMapping("/{id}/eligibility")
    public List<String> getEligibility(@PathVariable Long id) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        return eligibilityRepository.findByExamId(id).stream().map(ExamEligibility::getBatchName).toList();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteExam(@PathVariable Long id) {
        return eligibilityRepository.findByExamId(id).stream().map(ExamEligibility::getBatchName).toList();
    }

    /** Only draft or cancelled exams can be deleted (published ones must be cancelled first). */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteExam(@PathVariable Long id) {
        if (!isAdmin()) throw new javax.security.auth.login.AccountException("Admin access required.");
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + id));
        if (exam.getStatus() == Exam.ExamStatus.PUBLISHED) {
            throw new IllegalArgumentException(
                    "Cancel the exam before deleting it - it is currently published to students.");
        }
        seatAllocationRepository.deleteByExamId(id);
        eligibilityRepository.deleteByExamId(id);
        seatRepository.deleteAll(seatRepository.findByExamId(id));
        examRepository.deleteById(id);
        auditService.record(actor(), "EXAM_DELETED", "exam:" + id, label(exam));
        return Map.of("deleted", true);
    }

    private String label(Exam exam) {
        return (exam.getSubjectName() == null ? "exam" : exam.getSubjectName())
                + (exam.getExamDate() == null ? "" : " on " + exam.getExamDate());
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication() == null
                ? "system" : SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
