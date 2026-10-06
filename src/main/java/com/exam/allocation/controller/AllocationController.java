package com.exam.allocation.controller;

import com.exam.allocation.model.Exam;
import com.exam.allocation.model.Room;
import com.exam.allocation.model.Seat;
import com.exam.allocation.model.Student;
import com.exam.allocation.repository.ExamRepository;
import com.exam.allocation.repository.RoomRepository;
import com.exam.allocation.repository.SeatRepository;
import com.exam.allocation.repository.StudentRepository;
import com.exam.allocation.service.AllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class AllocationController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;
    
    @Autowired
    private ExamRepository examRepository;
    
    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private AllocationService allocationService;

    // --- Student Endpoints ---
    @GetMapping("/students")
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @PostMapping("/students")
    public Student addStudent(@RequestBody Student student) {
        return studentRepository.save(student);
    }

    // --- Room Endpoints ---
    @GetMapping("/rooms")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @PostMapping("/rooms")
    public Room addRoom(@RequestBody Room room) {
        return roomRepository.save(room);
    }

    // --- Allocation Endpoints ---
    @PostMapping("/allocate")
    public String generateAllocation() {
        // For simplicity in the demo, we will create a mock exam if none exist
        List<Exam> exams = examRepository.findAll();
        Exam exam;
        if (exams.isEmpty()) {
            exam = new Exam();
            exam.setSubjectName("General Exam");
            exam.setCourse("All Courses");
            exam.setExamDate(LocalDate.now());
            exam.setExamTime(LocalTime.now());
            exam = examRepository.save(exam);
        } else {
            exam = exams.get(0);
        }

        List<Long> roomIds = roomRepository.findAll().stream().map(Room::getId).collect(Collectors.toList());
        if (roomIds.isEmpty()) {
            return "Error: No rooms available for allocation.";
        }
        
        allocationService.generateAllocation(exam.getId(), roomIds);
        return "Allocation generated successfully!";
    }
    
    @GetMapping("/seats")
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }
}
