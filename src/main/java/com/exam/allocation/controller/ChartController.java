package com.exam.allocation.controller;

import com.exam.allocation.model.Exam;
import com.exam.allocation.model.Seat;
import com.exam.allocation.repository.ExamRepository;
import com.exam.allocation.repository.SeatRepository;
import com.exam.allocation.service.ChartPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Downloads the seating chart of an exam as a PDF file. */
@RestController
@RequestMapping("/api/chart")
public class ChartController {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ChartPdfService chartPdfService;

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadChart(@RequestParam Long examId) throws IOException {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found with id " + examId));

        List<Seat> seats = seatRepository.findByExamId(examId);
        if (seats.isEmpty()) {
            throw new IllegalArgumentException(
                    "No seating chart exists for this exam yet. Generate the allocation first.");
        }
        seats.sort(Comparator
                .comparing((Seat s) -> s.getRoom() == null ? 0L : s.getRoom().getId())
                .thenComparingInt(s -> s.getRowNumber())
                .thenComparingInt(s -> s.getColumnNumber()));

        byte[] pdf = chartPdfService.buildChartPdf(exam, seats);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdfFileName(exam) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String pdfFileName(Exam exam) {
        StringBuilder name = new StringBuilder("seating-chart");
        String group = exam.getCourse() != null && !exam.getCourse().isBlank()
                ? exam.getCourse() : exam.getBranch();
        if (group != null && !group.isBlank()) {
            name.append('-').append(sanitize(group));
        }
        if (exam.getSubjectName() != null && !exam.getSubjectName().isBlank()) {
            name.append('-').append(sanitize(exam.getSubjectName()));
        }
        name.append('-').append(exam.getExamDate() != null ? exam.getExamDate() : LocalDate.now());
        return name + ".pdf";
    }

    private String sanitize(String value) {
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9]+", "-");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }
}
