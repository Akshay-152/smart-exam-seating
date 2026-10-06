package com.exam.allocation.service;

import com.exam.allocation.model.Exam;
import com.exam.allocation.model.Room;
import com.exam.allocation.model.Seat;
import com.exam.allocation.model.Student;
import com.exam.allocation.repository.ExamRepository;
import com.exam.allocation.repository.RoomRepository;
import com.exam.allocation.repository.SeatRepository;
import com.exam.allocation.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AllocationService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ExamRepository examRepository;

    /**
     * Core Algorithm: Generates seating arrangement for a given exam and list of rooms.
     * Ensures students from the same course do not sit adjacent to each other.
     */
    @Transactional
    public void generateAllocation(Long examId, List<Long> roomIds) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Exam ID"));

        List<Student> studentsToAllocate = studentRepository.findAll(); 
        
        Map<String, List<Student>> studentsByCourse = studentsToAllocate.stream()
                .collect(Collectors.groupingBy(Student::getCourse));

        List<String> activeCourses = new ArrayList<>(studentsByCourse.keySet());
        java.util.Map<String, Integer> coursePointers = new java.util.HashMap<>();
        for (String course : activeCourses) {
            coursePointers.put(course, 0);
        }

        List<Room> rooms = roomRepository.findAllById(roomIds);
        int currentCourseIndex = 0;

        for (Room room : rooms) {
            int rows = room.getRowsCount();
            int cols = room.getColumnsCount();
            
            if (room.getSeats() == null || room.getSeats().isEmpty()) {
                room.setSeats(new ArrayList<>());
                for (int r = 1; r <= rows; r++) {
                    for (int c = 1; c <= cols; c++) {
                        Seat seat = new Seat();
                        seat.setRowNumber(r);
                        seat.setColumnNumber(c);
                        seat.setRoom(room);
                        room.getSeats().add(seat);
                    }
                }
                roomRepository.save(room);
            }

            // Allocate students sequentially into the room's seats using round-robin course strategy
            for (Seat seat : room.getSeats()) {
                if (activeCourses.isEmpty()) {
                    break; // All students allocated
                }

                if (seat.getAllocatedStudent() != null) continue; // Skip if already occupied

                String courseToAssign = activeCourses.get(currentCourseIndex);
                List<Student> courseStudents = studentsByCourse.get(courseToAssign);
                int studentPointer = coursePointers.get(courseToAssign);

                Student student = courseStudents.get(studentPointer);
                
                // Assign to seat
                seat.setAllocatedStudent(student);
                seat.setExam(exam);
                seatRepository.save(seat);

                studentPointer++;
                
                // If course is fully allocated, remove it from active rotation
                if (studentPointer >= courseStudents.size()) {
                    activeCourses.remove(currentCourseIndex);
                    if (activeCourses.isEmpty()) break;
                    if (currentCourseIndex >= activeCourses.size()) {
                        currentCourseIndex = 0;
                    }
                } else {
                    coursePointers.put(courseToAssign, studentPointer);
                    currentCourseIndex = (currentCourseIndex + 1) % activeCourses.size();
                }
            }
        }
    }
}
