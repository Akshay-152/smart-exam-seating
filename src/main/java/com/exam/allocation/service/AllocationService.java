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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Autowired
    private ConflictService conflictService;

    /** Result of an allocation run, returned as JSON to the frontend. */
    public static class AllocationResult {
        private boolean success;
        private String message;
        private int eligibleStudents;
        private int allocated;
        private int unallocated;
        private int roomsUsed;
        private List<String> conflicts = new ArrayList<>();

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
        public List<String> getConflicts() { return conflicts; }
        public void setConflicts(List<String> conflicts) { this.conflicts = conflicts; }
    }

    /**
     * Core Algorithm: Generates the seating chart for a given exam across the given rooms.
     *
     * - Only students matching the exam's branch/batch are seated (blank = "all").
     * - Students from different branch/batch groups are interleaved round-robin so that
     *   students of the same group are spread out instead of sitting in an adjacent block.
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
            result.setMessage("No students match this exam (batch/course: " + valueOrAll(exam.getCourse())
                    + ", branch: " + valueOrAll(exam.getBranch()) + ").");
            return result;
        }

        Map<String, List<Student>> studentsByCourse = eligible.stream()
                .collect(Collectors.groupingBy(AllocationService::groupKey, LinkedHashMap::new, Collectors.toList()));
        List<Student> sequence = interleave(studentsByCourse);

        // Work queue holding the exact existing round-robin order; every desk draws
        // its students from the front of this queue (see fillDesk for the subject-mixing rule).
        LinkedList<Student> queue = new LinkedList<>(sequence);
        int allocated = 0;
        int roomsUsed = 0;

        for (Room room : rooms) {
            if (queue.isEmpty()) break;
            int rows = room.getRowsCount();
            int cols = room.getColumnsCount();
            if (rows <= 0 || cols <= 0) continue;

            List<Seat> seats = prepareSeats(room, exam, rows, cols, room.getStudentsPerDesk());
            if (seats.isEmpty()) continue;
            roomsUsed++;

            // Seats arrive ordered row/column/position, so consecutive seats that share
            // the same row and column form one desk - fill the room desk by desk.
            List<Seat> desk = new ArrayList<>();
            for (Seat seat : seats) {
                if (!desk.isEmpty() && (seat.getRowNumber() != desk.get(0).getRowNumber()
                        || seat.getColumnNumber() != desk.get(0).getColumnNumber())) {
                    allocated += fillDesk(desk, queue, exam);
                    desk.clear();
                }
                desk.add(seat);
            }
            if (!desk.isEmpty()) allocated += fillDesk(desk, queue, exam);

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

        // Scheduling conflicts: students or rooms booked twice in this time slot.
        List<Seat> seated = new ArrayList<>();
        for (Room room : rooms) {
            seated.addAll(seatRepository.findByRoomId(room.getId()).stream()
                    .filter(s -> s.getExam() != null && exam.getId().equals(s.getExam().getId()))
                    .collect(Collectors.toList()));
        }
        result.setConflicts(conflictService.allocationWarnings(exam, seated));
        return result;
    }

    /**
     * Fills one desk (every student position of a single row/column cell) from the
     * front of the queue and returns how many students were seated.
     *
     * <p><b>Subject/Course Mixing Rule (enhancement):</b> while filling the desk, a
     * student whose course differs from the courses already seated at this desk is
     * preferred, so that e.g. {@code CS | OOP | CS} is chosen over {@code CS | CS | CS}
     * whenever OOP students are still available. All existing rules are preserved:
     * students are still drawn from the original round-robin sequence (only their
     * order within/near this desk may be swapped), no student is ever duplicated or
     * lost (unused candidates go back to the front of the queue in their original
     * order), never more than {@code deskSeats.size()} students are placed, and when
     * no different course is left the next student in the sequence is used as before.
     * The choice is deterministic: always the earliest fitting student in sequence order.
     */
    private int fillDesk(List<Seat> deskSeats, LinkedList<Student> queue, Exam exam) {
        int needed = deskSeats.size();
        if (queue.isEmpty() || needed == 0) return 0;

        // Look ahead far enough to see every course that is still available, so a
        // different subject just past the desk's own slice can still be pulled in.
        int distinctRemaining = (int) queue.stream()
                .map(AllocationService::groupKey)
                .map(c -> c.toLowerCase())
                .distinct()
                .count();
        int windowSize = Math.min(queue.size(), needed + distinctRemaining);

        List<Student> window = new ArrayList<>(windowSize);
        for (int i = 0; i < windowSize; i++) window.add(queue.pollFirst());

        Set<String> coursesAtDesk = new HashSet<>();
        List<Student> picked = new ArrayList<>(needed);
        for (int k = 0; k < needed && !window.isEmpty(); k++) {
            int chosen = -1;
            for (int j = 0; j < window.size(); j++) {
                if (!coursesAtDesk.contains(groupKey(window.get(j)).toLowerCase())) {
                    chosen = j;   // a branch/batch not yet sitting at this desk
                    break;
                }
            }
            if (chosen == -1) chosen = 0;   // all remaining groups already present: keep sequence order
            Student student = window.remove(chosen);
            coursesAtDesk.add(groupKey(student).toLowerCase());
            picked.add(student);
        }

        // Return the students we did not use to the FRONT of the queue in their
        // original order - nothing is skipped, duplicated or reordered for later desks.
        List<Student> rest = new ArrayList<>(window);
        rest.addAll(queue);
        queue.clear();
        queue.addAll(rest);

        int assigned = 0;
        for (int i = 0; i < deskSeats.size() && i < picked.size(); i++) {
            Seat seat = deskSeats.get(i);
            seat.setAllocatedStudent(picked.get(i));
            seat.setExam(exam);
            assigned++;
        }
        return assigned;
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
                .sorted(java.util.Comparator.comparingInt((Seat s) -> s.getRowNumber())
                        .thenComparingInt(s -> s.getColumnNumber())
                        .thenComparingInt(s -> s.getDeskPosition()))
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

    /** True when the student belongs to the audience of this exam (branch + batch/course). */
    private boolean matchesExam(Student student, Exam exam) {
        return branchMatches(student.getBranch(), exam.getBranch())
                && courseMatches(student, exam);
    }

    private boolean branchMatches(String studentBranch, String examBranch) {
        String wanted = norm(examBranch);
        if (isAll(wanted)) return true;
        return wanted.equals(norm(studentBranch));
    }

    /**
     * The exam's batch/course matches the student's batch first; a branch of the
     * same name also matches so imported/legacy "course" values keep working.
     */
    private boolean courseMatches(Student student, Exam exam) {
        String wanted = norm(exam.getCourse());
        if (isAll(wanted)) return true;
        return wanted.equals(norm(student.getBatch())) || wanted.equals(norm(student.getBranch()));
    }

    private boolean isAll(String norm) {
        return norm.isEmpty() || norm.equals("all") || norm.equals("*")
                || norm.equals("any") || norm.equals("all courses");
    }

    /** Grouping key used for interleaving, desk mixing, colours and the PDF legend. */
    public static String groupKey(Student s) {
        String branch = (s.getBranch() == null || s.getBranch().isBlank()) ? "Unbranched" : s.getBranch().trim();
        String batch = (s.getBatch() == null || s.getBatch().isBlank()) ? "Unbatched" : s.getBatch().trim();
        return branch + "\u00b7" + batch;
    }

    private String norm(String v) { return v == null ? "" : v.trim().toLowerCase(); }

    private String valueOrAll(String v) { return (v == null || v.isBlank()) ? "all" : v; }
}
