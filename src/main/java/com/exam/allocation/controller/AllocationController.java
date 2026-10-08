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

import java.util.Comparator;
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
        ensureRollNoFree(student.getRollNo(), null);
        return studentRepository.save(student);
    }

    @PutMapping("/students/{id}")
    public Student updateStudent(@PathVariable Long id, @RequestBody Student student) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with id " + id));
        ensureRollNoFree(student.getRollNo(), id);
        existing.setRollNo(student.getRollNo());
        existing.setName(student.getName());
        existing.setBranch(student.getBranch());
        existing.setBatch(student.getBatch());
        return studentRepository.save(existing);
    }

    @DeleteMapping("/students/{id}")
    public Map<String, Object> deleteStudent(@PathVariable Long id) {
        if (!studentRepository.existsById(id)) {
            throw new IllegalArgumentException("Student not found with id " + id);
        }
        // Clear seat references first so the foreign key does not block deletion.
        List<Seat> seated = seatRepository.findByAllocatedStudentId(id);
        seated.forEach(seat -> seat.setAllocatedStudent(null));
        seatRepository.saveAll(seated);
        studentRepository.deleteById(id);
        return Map.of("deleted", true, "clearedSeats", seated.size());
    }

    // --- Room Endpoints ---

    @GetMapping("/rooms")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @PostMapping("/rooms")
    public Room addRoom(@RequestBody Room room) {
        ensureRoomNumberFree(room.getRoomNumber(), null);
        return roomRepository.save(room);
    }

    @PutMapping("/rooms/{id}")
    public Room updateRoom(@PathVariable Long id, @RequestBody Room room) {
        Room existing = roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with id " + id));
        ensureRoomNumberFree(room.getRoomNumber(), id);
        existing.setRoomNumber(room.getRoomNumber());
        existing.setCapacity(room.getCapacity());
        existing.setRowsCount(room.getRowsCount());
        existing.setColumnsCount(room.getColumnsCount());
        existing.setStudentsPerDesk(room.getStudentsPerDesk());
        return roomRepository.save(existing);
    }

    @DeleteMapping("/rooms/{id}")
    public Map<String, Object> deleteRoom(@PathVariable Long id) {
        if (!roomRepository.existsById(id)) {
            throw new IllegalArgumentException("Room not found with id " + id);
        }
        seatRepository.deleteAll(seatRepository.findByRoomId(id));
        roomRepository.deleteById(id);
        return Map.of("deleted", true);
    }

    // --- Allocation Endpoints ---

    /**
     * Generates the seating chart for an exam.
     * Body: {"examId": 1} - when omitted, the most recent exam is used.
     */
    @PostMapping("/allocate")
    public AllocationService.AllocationResult generateAllocation(
            @RequestBody(required = false) Map<String, Object> body) {

        Long examId = null;
        if (body != null && body.get("examId") != null) {
            examId = Long.valueOf(String.valueOf(body.get("examId")));
        }

        if (examId == null) {
            List<Exam> exams = examRepository.findAll();
            if (exams.isEmpty()) {
                return failure("No exams found. Create an exam first, then generate the chart.");
            }
            examId = exams.stream()
                    .max(Comparator.comparing(e -> e.getId()))
                    .map(e -> e.getId())
                    .orElse(null);
        }

        List<Long> roomIds = roomRepository.findAll().stream()
                .map(r -> r.getId())
                .collect(Collectors.toList());
        if (roomIds.isEmpty()) {
            return failure("No rooms available for allocation. Add a room first.");
        }

        return allocationService.generateAllocation(examId, roomIds);
    }

    // --- Seat / Chart Endpoints ---

    /** All seats, or only the seats of one exam when ?examId= is given. */
    @GetMapping("/seats")
    public List<Seat> getAllSeats(@RequestParam(required = false) Long examId) {
        List<Seat> seats = examId != null
                ? seatRepository.findByExamId(examId)
                : seatRepository.findAll();
        seats.sort(Comparator
                .comparing((Seat s) -> s.getRoom() == null ? 0L : s.getRoom().getId())
                .thenComparingInt(s -> s.getRowNumber())
                .thenComparingInt(s -> s.getColumnNumber()));
        return seats;
    }

    // --- Helpers ---

    private AllocationService.AllocationResult failure(String message) {
        AllocationService.AllocationResult result = new AllocationService.AllocationResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    private void ensureRollNoFree(String rollNo, Long ignoreId) {
        if (rollNo == null || rollNo.isBlank()) return;
        Student other = studentRepository.findByRollNo(rollNo);
        if (other != null && !other.getId().equals(ignoreId)) {
            throw new IllegalArgumentException("Roll No \"" + rollNo + "\" already exists.");
        }
    }

    private void ensureRoomNumberFree(String roomNumber, Long ignoreId) {
        if (roomNumber == null || roomNumber.isBlank()) return;
        Room other = roomRepository.findByRoomNumber(roomNumber);
        if (other != null && !other.getId().equals(ignoreId)) {
            throw new IllegalArgumentException("Room \"" + roomNumber + "\" already exists.");
        }
    }
}
