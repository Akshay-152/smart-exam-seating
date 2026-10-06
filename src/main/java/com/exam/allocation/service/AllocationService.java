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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** Result of an allocation run, returned as JSON to the frontend. */
    public static class AllocationResult {
        private boolean success;
        private String message;
        private int eligibleStudents;
        private int allocated;
        private int unallocated;
        private int roomsUsed;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getEligibleStudents() { return eligibleStudents; }
        public void setEligibleStudents(int eligibleStudents) { this.eligibleStudents = eligibleStudents; }
        public int getAllocated() { return allocated; }
        public void setAllocated(int allocated) { this.allocated = allocated; }
        public int getUnallocated() { return unallocated; }
        public void setUnallocated(int unallocated) { this.unallocated = unallocated; }
        public int getRoomsUsed() { return roomsUsed; }
        public void setRoomsUsed(int roomsUsed) { this.roomsUsed = roomsUsed; }
    }

    /**
     * Core Algorithm: Generates the seating chart for a given exam across the given rooms.
     *
     * - Only students matching the exam's course/semester are seated (blank = "all").
     * - Students from different courses are interleaved round-robin so that students of
     *   the same course are spread out instead of sitting in an adjacent block.
     * - Any previous chart for the same exam is cleared first, so the operation can be
     *   re-run safely (the old implementation crashed with a unique-constraint error).
     */
    @Transactional
    public AllocationResult generateAllocation(Long examId, List<Long> roomIds) {
        AllocationResult result = new AllocationResult();

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid Exam ID"));

        List<Room> rooms = roomRepository.findAllById(roomIds);
        if (rooms.isEmpty()) {
            result.setMessage("No rooms available for allocation. Add a room first.");
            return result;
        }

        List<Student> eligible = studentRepository.findAll().stream()
                .filter(s -> matchesExam(s, exam))
                .collect(Collectors.toList());
        result.setEligibleStudents(eligible.size());

        if (eligible.isEmpty()) {
            result.setMessage("No students match this exam (course: " + valueOrAll(exam.getCourse())
                    + ", semester: " + valueOrAll(exam.getSemester()) + ").");
            return result;
        }

        Map<String, List<Student>> studentsByCourse = eligible.stream()
                .collect(Collectors.groupingBy(AllocationService::courseKey, LinkedHashMap::new, Collectors.toList()));
        List<Student> sequence = interleave(studentsByCourse);

        int allocated = 0;
        int roomsUsed = 0;
        int next = 0;

        for (Room room : rooms) {
            if (next >= sequence.size()) break;
            int rows = room.getRowsCount();
            int cols = room.getColumnsCount();
            if (rows <= 0 || cols <= 0) continue;

            List<Seat> seats = prepareSeats(room, exam, rows, cols, room.getStudentsPerDesk());
            if (seats.isEmpty()) continue;
            roomsUsed++;

            for (Seat seat : seats) {
                if (next >= sequence.size()) break;
                Student student = sequence.get(next++);
                seat.setAllocatedStudent(student);
                seat.setExam(exam);
                allocated++;
            }
            seatRepository.saveAll(seats);
        }

        result.setAllocated(allocated);
        result.setUnallocated(sequence.size() - allocated);
        result.setRoomsUsed(roomsUsed);
        result.setSuccess(true);

        StringBuilder msg = new StringBuilder("Seating chart generated: " + allocated + " of "
                + sequence.size() + " student(s) seated in " + roomsUsed + " room(s).");
        if (allocated < sequence.size()) {
            msg.append(" ").append(sequence.size() - allocated)
               .append(" student(s) could not be seated - add more rooms or larger rooms.");
        }
        result.setMessage(msg.toString());
        return result;
    }

    /**
     * Returns the seats of this room for this exam, ordered row/column/position.
     * Each desk (grid cell) holds {@code studentsPerDesk} seats, so a desk that
     * seats two students gets two seat rows at the same row/column.
     * Creates the grid if it does not exist yet (or if the room layout changed),
     * and clears any previous student assignment so re-running is idempotent.
     */
    private List<Seat> prepareSeats(Room room, Exam exam, int rows, int cols, int perDesk) {
        if (perDesk < 1) perDesk = 1;
        int total = rows * cols * perDesk;

        List<Seat> existing = seatRepository.findByRoomId(room.getId()).stream()
                .filter(s -> s.getExam() != null && exam.getId() != null && exam.getId().equals(s.getExam().getId()))
                .sorted(java.util.Comparator.comparingInt(Seat::getRowNumber)
                        .thenComparingInt(Seat::getColumnNumber)
                        .thenComparingInt(Seat::getDeskPosition))
                .collect(Collectors.toList());

        if (existing.size() != total) {
            if (!existing.isEmpty()) {
                seatRepository.deleteAll(existing);
                seatRepository.flush();
            }
            List<Seat> created = new ArrayList<>();
            for (int r = 1; r <= rows; r++) {
                for (int c = 1; c <= cols; c++) {
                    for (int p = 1; p <= perDesk; p++) {
                        Seat seat = new Seat();
                        seat.setRowNumber(r);
                        seat.setColumnNumber(c);
                        seat.setDeskPosition(p);
                        seat.setRoom(room);
                        seat.setExam(exam);
                        created.add(seat);
                    }
                }
            }
            return seatRepository.saveAll(created);
        }

        // Re-run: wipe previous chart of this exam, keep the physical desks.
        existing.forEach(s -> s.setAllocatedStudent(null));
        return existing;
    }

    /** Round-robin one student from each course per pass, until every student is placed. */
    private List<Student> interleave(Map<String, List<Student>> byCourse) {
        List<String> courses = new ArrayList<>(byCourse.keySet());
        Collections.sort(courses);
        Map<String, Integer> pointers = new HashMap<>();
        for (String c : courses) pointers.put(c, 0);

        List<Student> out = new ArrayList<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (String c : courses) {
                int p = pointers.get(c);
                if (p < byCourse.get(c).size()) {
                    out.add(byCourse.get(c).get(p));
                    pointers.put(c, p + 1);
                    progress = true;
                }
            }
        }
        return out;
    }

    /** True when the student belongs to the audience of this exam (course + semester). */
    private boolean matchesExam(Student student, Exam exam) {
        return courseMatches(student.getCourse(), exam.getCourse())
                && semesterMatches(student.getCurrentSemester(), exam.getSemester());
    }

    private boolean courseMatches(String studentCourse, String examCourse) {
        String wanted = norm(examCourse);
        if (wanted.isEmpty() || wanted.equals("all") || wanted.equals("*") || wanted.equals("all courses")) {
            return true;
        }
        return wanted.equals(norm(studentCourse));
    }

    private boolean semesterMatches(String studentSem, String examSem) {
        String wanted = norm(examSem);
        if (wanted.isEmpty() || wanted.equals("all") || wanted.equals("*")) {
            return true;
        }
        // Compare "S3" and "3" and "sem 3" as equal by their digits when both sides have digits.
        String wantedDigits = wanted.replaceAll("\\D", "");
        String studentDigits = norm(studentSem).replaceAll("\\D", "");
        if (!wantedDigits.isEmpty() && !studentDigits.isEmpty()) {
            return wantedDigits.equals(studentDigits);
        }
        return wanted.equals(norm(studentSem));
    }

    private static String courseKey(Student s) {
        String course = s.getCourse();
        return (course == null || course.isBlank()) ? "Unspecified" : course.trim();
    }

    private String norm(String v) { return v == null ? "" : v.trim().toLowerCase(); }

    private String valueOrAll(String v) { return (v == null || v.isBlank()) ? "all" : v; }
}
