package com.exam.allocation.service;

import com.exam.allocation.model.Exam;
import com.exam.allocation.model.Seat;
import com.exam.allocation.model.Student;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the seating chart for an exam as a landscape A4 PDF:
 * one section per room drawn as a grid of seats, colour-coded by course.
 */
@Service
public class ChartPdfService {

    private static final float PAGE_W = PDRectangle.A4.getHeight(); // landscape A4
    private static final float PAGE_H = PDRectangle.A4.getWidth();
    private static final float MARGIN = 36f;
    private static final float BOTTOM = 46f;
    private static final float ROW_LABEL_W = 24f;
    private static final float COL_LABEL_H = 14f;
    private static final float CELL_H = 42f;

    private static final int[][] PALETTE = {
        {79, 70, 229},   // indigo
        {16, 185, 129},  // green
        {245, 158, 11},  // amber
        {239, 68, 68},   // red
        {14, 165, 233},  // sky
        {168, 85, 247},  // purple
        {236, 72, 153},  // pink
        {20, 184, 166},  // teal
    };

    /** Drawing state: current page stream, y position and page counter. */
    private static class Cursor {
        final PDDocument doc;
        PDPageContentStream cs;
        float y;
        int pageCount;

        Cursor(PDDocument doc) { this.doc = doc; }
    }

    public byte[] buildChartPdf(Exam exam, List<Seat> seats) throws IOException {
        try (PDDocument document = new PDDocument()) {
            Cursor cursor = new Cursor(document);
            newPage(cursor);

            drawHeader(cursor, exam);
            Map<String, Integer[]> courses = courseColors(seats);
            drawLegend(cursor, courses);

            Map<Long, List<Seat>> byRoom = new LinkedHashMap<>();
            for (Seat seat : seats) {
                Long roomId = seat.getRoom() == null ? -1L : seat.getRoom().getId();
                byRoom.computeIfAbsent(roomId, k -> new ArrayList<>()).add(seat);
            }
            for (List<Seat> roomSeats : byRoom.values()) {
                roomSeats.sort(Comparator.comparingInt(Seat::getRowNumber)
                        .thenComparingInt(Seat::getColumnNumber));
                drawRoom(cursor, roomSeats, courses);
            }

            cursor.cs.close();
            drawFooters(document);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ pages

    private void newPage(Cursor cursor) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
        cursor.doc.addPage(page);
        cursor.cs = new PDPageContentStream(cursor.doc, page);
        cursor.y = PAGE_H - MARGIN;
        cursor.pageCount++;
    }

    /** Starts a fresh page when height no longer fits; returns true when that happened. */
    private boolean ensureSpace(Cursor cursor, float height) throws IOException {
        if (cursor.y - height >= BOTTOM) return false;
        cursor.cs.close();
        newPage(cursor);
        return true;
    }

    // ------------------------------------------------------------------ blocks

    private void drawHeader(Cursor cursor, Exam exam) throws IOException {
        PDPageContentStream cs = cursor.cs;
        centered(cs, cursor.y - 4, "SEATING CHART", PDType1Font.HELVETICA_BOLD, 18);
        cursor.y -= 24;
        centered(cs, cursor.y, value(exam.getSubjectName(), "Exam"), PDType1Font.HELVETICA_BOLD, 13);
        cursor.y -= 18;
        String meta = "Semester: " + value(exam.getSemester(), "-")
                + "     Course: " + value(exam.getCourse(), "-")
                + "     Date: " + value(exam.getExamDate() == null ? null : exam.getExamDate().toString(), "-")
                + "     Time: " + value(exam.getExamTime() == null ? null : exam.getExamTime().toString(), "-")
                + "     Application ID: " + value(exam.getApplicationId(), "-");
        centered(cs, cursor.y, meta, PDType1Font.HELVETICA, 9);
        cursor.y -= 13;
        centered(cs, cursor.y, "Generated: " + LocalDate.now() + "  "
                + LocalTime.now().withNano(0).format(DateTimeFormatter.ofPattern("HH:mm")),
                PDType1Font.HELVETICA, 8);
        cursor.y -= 10;

        cs.setStrokingColor(79, 70, 229);
        cs.setLineWidth(1.4f);
        cs.moveTo(MARGIN, cursor.y);
        cs.lineTo(PAGE_W - MARGIN, cursor.y);
        cs.stroke();
        cs.setStrokingColor(0, 0, 0);
        cursor.y -= 22;
    }

    private void drawLegend(Cursor cursor, Map<String, Integer[]> courses) throws IOException {
        if (courses.isEmpty()) return;
        ensureSpace(cursor, 20);
        PDPageContentStream cs = cursor.cs;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
        cs.newLineAtOffset(MARGIN, cursor.y);
        cs.showText("Courses:");
        cs.endText();

        float x = MARGIN + 54;
        for (Map.Entry<String, Integer[]> entry : courses.entrySet()) {
            Integer[] rgb = entry.getValue();
            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.addRect(x, cursor.y - 1, 8, 8);
            cs.fill();
            cs.setNonStrokingColor(30, 30, 30);
            String label = entry.getKey() + " (" + rgb[3] + ")";
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 8);
            cs.newLineAtOffset(x + 11, cursor.y - 1);
            cs.showText(sanitize(label));
            cs.endText();
            x += 18 + label.length() * 4.3f;
            if (x > PAGE_W - MARGIN - 80) break;
        }
        cursor.y -= 20;
    }

    private void drawRoom(Cursor cursor, List<Seat> roomSeats, Map<String, Integer[]> courses) throws IOException {
        if (roomSeats.isEmpty()) return;
        Seat first = roomSeats.get(0);
        int rows = roomSeats.get(roomSeats.size() - 1).getRowNumber();
        int cols = roomSeats.stream().mapToInt(Seat::getColumnNumber).max().orElse(1);
        String roomNumber = first.getRoom() == null ? "?" : first.getRoom().getRoomNumber();

        ensureSpace(cursor, CELL_H + COL_LABEL_H + 40);
        cursor.y -= 6;

        PDPageContentStream cs = cursor.cs;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
        cs.newLineAtOffset(MARGIN, cursor.y);
        cs.showText(sanitize("Room " + roomNumber + "   (" + rows + " rows x " + cols
                + " columns, " + roomSeats.size() + " seats)"));
        cs.endText();
        cursor.y -= 16;

        float gridW = PAGE_W - 2 * MARGIN - ROW_LABEL_W;
        float cellW = Math.min(130f, Math.max(20f, gridW / Math.max(1, cols)));
        float x0 = MARGIN + ROW_LABEL_W;

        drawColumnLabels(cursor, x0, cellW, cols);
        cursor.y -= COL_LABEL_H;

        for (int r = 1; r <= rows; r++) {
            if (ensureSpace(cursor, CELL_H)) {
                // Continued on a new page: repeat the column labels first.
                drawColumnLabels(cursor, x0, cellW, cols);
                cursor.y -= COL_LABEL_H;
            }
            drawRoomRow(cursor, r, cols, x0, cellW, roomSeats, courses);
        }
        cursor.y -= 28;
    }

    private void drawColumnLabels(Cursor cursor, float x0, float cellW, int cols) throws IOException {
        PDPageContentStream cs = cursor.cs;
        for (int c = 1; c <= cols; c++) {
            centeredAt(cs, x0 + (c - 0.5f) * cellW, cursor.y - 9,
                    "C" + c, PDType1Font.HELVETICA, 7);
        }
    }

    private void drawRoomRow(Cursor cursor, int row, int cols, float x0, float cellW,
                             List<Seat> roomSeats, Map<String, Integer[]> courses) throws IOException {
        PDPageContentStream cs = cursor.cs;
        float rowTop = cursor.y;
        float rowBottom = rowTop - CELL_H;

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
        cs.newLineAtOffset(MARGIN, rowTop - CELL_H / 2 - 3);
        cs.showText("R" + row);
        cs.endText();

        for (int c = 1; c <= cols; c++) {
            final int rr = row, cc = c;
            Seat seat = roomSeats.stream()
                    .filter(s -> s.getRowNumber() == rr && s.getColumnNumber() == cc)
                    .findFirst().orElse(null);
            drawSeat(cursor, x0 + (c - 1) * cellW, rowBottom, cellW, CELL_H, seat, courses);
        }

        cs.setStrokingColor(209, 213, 219);
        cs.setLineWidth(0.6f);
        cs.moveTo(x0, rowBottom);
        cs.lineTo(x0 + cols * cellW, rowBottom);
        cs.stroke();
        cs.setStrokingColor(0, 0, 0);

        cursor.y = rowBottom;
    }

    private void drawSeat(Cursor cursor, float x, float y, float w, float h,
                          Seat seat, Map<String, Integer[]> courses) throws IOException {
        PDPageContentStream cs = cursor.cs;
        boolean vacant = seat == null || seat.getAllocatedStudent() == null;
        int[] rgb = vacant ? new int[]{243, 244, 246} : colorFor(seat.getAllocatedStudent().getCourse(), courses);

        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setStrokingColor(156, 163, 175);
        cs.setLineWidth(0.6f);
        cs.addRect(x, y, w, h);
        cs.stroke();

        if (vacant) {
            centeredAt(cs, x + w / 2, y + h / 2 - 3, "VACANT", PDType1Font.HELVETICA, 7);
            return;
        }

        Student student = seat.getAllocatedStudent();
        boolean dark = rgb[0] + rgb[1] + rgb[2] < 400;
        cs.setNonStrokingColor(dark ? 255 : 17, dark ? 255 : 24, dark ? 255 : 39);

        centeredAt(cs, x + w / 2, y + h - 15,
                fit(student.getRollNo(), (int) Math.max(4, w / 5.5)), PDType1Font.HELVETICA_BOLD, 9);
        centeredAt(cs, x + w / 2, y + h - 27,
                fit(student.getName(), (int) Math.max(4, w / 4.6)), PDType1Font.HELVETICA, 7.5f);
        centeredAt(cs, x + w / 2, y + 6,
                fit(student.getCourse(), (int) Math.max(4, w / 4.0)), PDType1Font.HELVETICA, 6.5f);
    }

    private void drawFooters(PDDocument document) throws IOException {
        int total = document.getPages().getCount();
        String stamp = "Room Allocation System - generated " + LocalDate.now();
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                cs.setNonStrokingColor(107, 114, 128);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 7.5f);
                cs.newLineAtOffset(MARGIN, BOTTOM - 16);
                cs.showText(sanitize(stamp));
                cs.endText();
                String pageNo = "Page " + (i + 1) + " of " + total;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 7.5f);
                cs.newLineAtOffset(PAGE_W - MARGIN - pageNo.length() * 4.0f, BOTTOM - 16);
                cs.showText(pageNo);
                cs.endText();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** course -> {r, g, b, allocatedCount} in first-seen order. */
    private Map<String, Integer[]> courseColors(List<Seat> seats) {
        Map<String, Integer[]> map = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Seat seat : seats) {
            if (seat.getAllocatedStudent() == null) continue;
            String course = seat.getAllocatedStudent().getCourse();
            course = (course == null || course.isBlank()) ? "Unspecified" : course.trim();
            counts.merge(course, 1, Integer::sum);
        }
        int i = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int[] rgb = PALETTE[i % PALETTE.length];
            map.put(entry.getKey(), new Integer[]{rgb[0], rgb[1], rgb[2], entry.getValue()});
            i++;
        }
        return map;
    }

    private int[] colorFor(String course, Map<String, Integer[]> courses) {
        String key = (course == null || course.isBlank()) ? "Unspecified" : course.trim();
        Integer[] rgb = courses.get(key);
        return rgb == null ? new int[]{79, 70, 229} : new int[]{rgb[0], rgb[1], rgb[2]};
    }

    private void centered(PDPageContentStream cs, float y, String text,
                          PDType1Font font, float size) throws IOException {
        centeredAt(cs, PAGE_W / 2, y, text, font, size);
    }

    private void centeredAt(PDPageContentStream cs, float cx, float y, String text,
                            PDType1Font font, float size) throws IOException {
        String safe = sanitize(text);
        float width;
        try {
            width = font.getStringWidth(safe) / 1000f * size;
        } catch (IOException e) {
            width = safe.length() * size * 0.5f;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(Math.max(MARGIN, cx - width / 2), y);
        cs.showText(safe);
        cs.endText();
    }

    private String fit(String text, int maxChars) {
        if (text == null) return "-";
        if (maxChars < 4) maxChars = 4;
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "..";
    }

    /** Helvetica only supports Latin-1; replace anything outside it. */
    private String sanitize(String text) {
        if (text == null) return "-";
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            boolean ok = (c >= 32 && c < 127) || (c >= 160 && c <= 255);
            sb.append(ok ? c : '?');
        }
        return sb.toString();
    }

    private String value(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
