package com.exam.allocation.controller;

import com.exam.allocation.model.*;
import com.exam.allocation.repository.*;
import com.exam.allocation.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Administrator dashboard: live statistics, audit trail, announcements, departments, subjects. */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    @Autowired private StudentRepository studentRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private SeatAllocationRepository seatAllocationRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private AuditService auditService;

    /** Summary cards, computed from the database - nothing here is hardcoded. */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        List<Student> students = studentRepository.findAll();
        List<Exam> exams = examRepository.findAll();
        List<Room> rooms = roomRepository.findAll();

        Set<Long> allocatedStudentIds = seatAllocationRepository.findAll().stream()
                .filter(a -> a.getStatus() == SeatAllocation.Status.PUBLISHED)
                .map(a -> a.getStudent().getId())
                .collect(Collectors.toSet());

        long activeStudents = students.stream()
                .filter(s -> s.getAccountStatus() == Student.AccountStatus.ACTIVE).count();
        long upcoming = exams.stream()
                .filter(e -> e.getStatus() == Exam.ExamStatus.PUBLISHED)
                .filter(e -> e.getExamDate() != null && !e.getExamDate().isBefore(today))
                .count();
        long completed = exams.stream()
                .filter(e -> e.getStatus() == Exam.ExamStatus.COMPLETED
                        || (e.getStatus() == Exam.ExamStatus.PUBLISHED
                            && e.getExamDate() != null && e.getExamDate().isBefore(today)))
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStudents", activeStudents);
        stats.put("inactiveStudents", students.size() - activeStudents);
        stats.put("totalRooms", rooms.size());
        stats.put("activeRooms", rooms.stream().filter(Room::isActive).count());
        stats.put("totalBatches", batchRepository.count());
        stats.put("totalExams", exams.size());
        stats.put("draftExams", exams.stream().filter(e -> e.getStatus() == Exam.ExamStatus.DRAFT).count());
        stats.put("publishedExams", exams.stream().filter(e -> e.getStatus() == Exam.ExamStatus.PUBLISHED).count());
        stats.put("cancelledExams", exams.stream().filter(e -> e.getStatus() == Exam.ExamStatus.CANCELLED).count());
        stats.put("upcomingExams", upcoming);
        stats.put("completedExams", completed);
        stats.put("allocatedStudents", allocatedStudentIds.size());
        stats.put("unallocatedStudents", Math.max(0, activeStudents - allocatedStudentIds.size()));
        stats.put("recentActivities", auditService.recent().stream().limit(10).map(this::describeAudit).toList());
        return stats;
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(this::describeAudit).toList();
    }

    // --- Announcements ---

    @GetMapping("/announcements")
    public List<Announcement> announcements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/announcements")
    public Announcement createAnnouncement(@RequestBody Announcement body) {
        Announcement saved = announcementRepository.save(body);
        auditService.record(actor(), "ANNOUNCEMENT_CREATED", "announcement:" + saved.getId(), saved.getTitle());
        return saved;
    }

    @DeleteMapping("/announcements/{id}")
    public Map<String, Object> deleteAnnouncement(@PathVariable Long id) {
        announcementRepository.deleteById(id);
        auditService.record(actor(), "ANNOUNCEMENT_DELETED", "announcement:" + id, null);
        return Map.of("deleted", true);
    }

    // --- Departments ---

    @GetMapping("/departments")
    public List<Department> departments() {
        return departmentRepository.findAllByOrderByCodeAsc();
    }

    @PostMapping("/departments")
    public Department createDepartment(@RequestBody Department body) {
        if (departmentRepository.findByCodeIgnoreCase(body.getCode()) != null) {
            throw new IllegalArgumentException("Department \"" + body.getCode() + "\" already exists.");
        }
        return departmentRepository.save(body);
    }

    @DeleteMapping("/departments/{id}")
    public Map<String, Object> deleteDepartment(@PathVariable Long id) {
        departmentRepository.deleteById(id);
        return Map.of("deleted", true);
    }

    // --- Subjects ---

    @GetMapping("/subjects")
    public List<Subject> subjects() {
        return subjectRepository.findAllByOrderByCodeAsc();
    }

    @PostMapping("/subjects")
    public Subject createSubject(@RequestBody Subject body) {
        if (subjectRepository.findByCodeIgnoreCase(body.getCode()) != null) {
            throw new IllegalArgumentException("Subject \"" + body.getCode() + "\" already exists.");
        }
        Subject saved = subjectRepository.save(body);
        auditService.record(actor(), "SUBJECT_CREATED", "subject:" + saved.getId(), saved.getCode() + " - " + saved.getName());
        return saved;
    }

    @DeleteMapping("/subjects/{id}")
    public Map<String, Object> deleteSubject(@PathVariable Long id) {
        subjectRepository.deleteById(id);
        return Map.of("deleted", true);
    }

    // --- Helpers ---

    private Map<String, Object> describeAudit(AuditLog log) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", log.getId());
        out.put("actor", log.getActor());
        out.put("action", log.getAction());
        out.put("target", log.getTarget());
        out.put("details", log.getDetails());
        out.put("timestamp", log.getTimestamp().toString());
        return out;
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication() == null
                ? "system" : SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
