package com.exam.allocation.model;

import jakarta.persistence.*;

/**
 * A student participating in exams.
 *
 * <p>Stored information is intentionally limited to exactly four fields
 * (per the project specification - there is NO Division field):
 * <pre>
 * Roll Number | Name | Branch | Batch/Course
 * 1           | Akshay | CEC  | E
 * </pre>
 *
 * <p>Fields are nullable so that imports (PDF/Excel) with missing cells can be
 * stored as null.
 */
@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String rollNo;

    private String name;

    /** Branch / department the student belongs to, e.g. "CEC". */
    private String branch;

    /**
     * Batch / Course the student belongs to, e.g. "E".
     * Default batches are A, B, C, D, E, F, G; custom ones can be added
     * through the Courses tab (see {@link com.exam.allocation.model.Batch}).
     */
    private String batch;

    // Default constructor
    public Student() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
}
