package com.exam.allocation.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * An exam event that students can be seated for.
 *
 * <p>Audience targeting (both optional - blank means "all students"):
 * <ul>
 *   <li>{@code branch}  - only students of this branch, e.g. "CEC"</li>
 *   <li>{@code course}  - the batch/course the exam is for, e.g. "E"
 *       (also matches a branch of the same name for imported/legacy data)</li>
 * </ul>
 */
@Entity
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String subjectName;

    /** Branch this exam is for; null/blank = all branches. */
    private String branch;

    /** Batch/Course this exam is for, e.g. "E"; null/blank = all batches. */
    private String course;

    private LocalDate examDate;

    private LocalTime examTime;

    /** Application identifier of the exam, e.g. "APP-E-2026-001". */
    private String applicationId;

    public Exam() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }

    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

    public LocalTime getExamTime() { return examTime; }
    public void setExamTime(LocalTime examTime) { this.examTime = examTime; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
}
