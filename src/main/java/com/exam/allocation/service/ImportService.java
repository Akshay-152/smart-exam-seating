package com.exam.allocation.service;

import com.exam.allocation.model.Student;
import com.exam.allocation.repository.StudentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports student lists uploaded as Excel (.xls/.xlsx), PDF or CSV/TXT files.
 *
 * Header row is detected by known column names ("roll no", "name", "course", "semester",
 * ...); when it cannot be detected the default order rollNo/name/course/semester is used.
 * Any cell that is missing in the file is stored as null.
 */
@Service
public class ImportService {

    @Autowired
    private StudentRepository studentRepository;

    public static class ImportResult {
        private String fileName;
        private int imported;     // brand-new students
        private int updated;      // existing roll numbers updated
        private int skipped;      // empty / unrecognised rows
        private int missingCells; // cells stored as null
        private List<Map<String, String>> preview = new ArrayList<>();
        private List<String> notes = new ArrayList<>();

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public int getImported() { return imported; }
        public void setImported(int imported) { this.imported = imported; }
        public int getUpdated() { return updated; }
        public void setUpdated(int updated) { this.updated = updated; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public int getMissingCells() { return missingCells; }
        public void setMissingCells(int missingCells) { this.missingCells = missingCells; }
        public List<Map<String, String>> getPreview() { return preview; }
        public void setPreview(List<Map<String, String>> preview) { this.preview = preview; }
        public List<String> getNotes() { return notes; }
        public void setNotes(List<String> notes) { this.notes = notes; }
    }

    private static final String F_ROLL = "rollNo";
    private static final String F_NAME = "name";
    private static final String F_COURSE = "course";
    private static final String F_SEM = "currentSemester";

    public ImportResult importStudents(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded. Choose a .pdf, .xlsx, .xls or .csv file.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = original.toLowerCase();

        List<String[]> rows;
        boolean positionalTokens; // PDF rows may need token re-balancing (names contain spaces)
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            rows = readExcel(file);
            positionalTokens = false;
        } else if (lower.endsWith(".pdf")) {
            rows = readPdf(file);
            positionalTokens = true;
        } else if (lower.endsWith(".csv") || lower.endsWith(".txt") || lower.endsWith(".tsv")) {
            rows = readDelimited(file);
            positionalTokens = false;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type \"" + original + "\". Supported formats: .pdf, .xlsx, .xls, .csv, .txt");
        }

        ImportResult result = new ImportResult();
        result.setFileName(original);

        // ---- Locate header row and map columns -------------------------------
        int headerRow = -1;
        Map<String, Integer> mapping = new HashMap<>();
        int scanLimit = Math.min(rows.size(), 5);
        for (int i = 0; i < scanLimit; i++) {
            Map<String, Integer> candidate = mapHeader(rows.get(i));
            if (candidate.size() >= 2) {
                headerRow = i;
                mapping = candidate;
                break;
            }
        }
        if (headerRow < 0) {
            // No recognised header: assume the default column order.
            mapping.put(F_ROLL, 0);
            mapping.put(F_NAME, 1);
            mapping.put(F_COURSE, 2);
            mapping.put(F_SEM, 3);
            result.getNotes().add("No header row recognised - assumed column order: Roll No, Name, Course, Semester.");
        } else {
            result.getNotes().add("Header found in row " + (headerRow + 1) + " with "
                    + mapping.size() + " recognised column(s).");
        }

        int expected = mapping.values().stream().mapToInt(Integer::intValue).max().orElse(3) + 1;
        int nameIdx = mapping.getOrDefault(F_NAME, 1);

        // ---- Read each row into fields ---------------------------------------
        int startRow = headerRow >= 0 ? headerRow + 1 : 0;
        int processed = 0;
        for (int i = startRow; i < rows.size(); i++) {
            String[] cells = rows.get(i);
            if (isBlankRow(cells)) { result.setSkipped(result.getSkipped() + 1); continue; }
            if (headerRow < 0 && looksLikeHeader(cells)) {
                // A header that was not recognised by the first-pass scan (e.g. on page 2).
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }

            Map<String, String> fields = positionalTokens
                    ? extractByTokens(cells, mapping, expected, nameIdx)
                    : extractPositional(cells, mapping);

            String roll = blankToNull(fields.get(F_ROLL));
            String name = blankToNull(fields.get(F_NAME));
            String course = blankToNull(fields.get(F_COURSE));
            String sem = blankToNull(fields.get(F_SEM));

            if (roll == null && name == null && course == null && sem == null) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }

            int missing = countMissing(roll, name, course, sem);
            result.setMissingCells(result.getMissingCells() + missing);

            Student student;
            if (roll != null) {
                Student existing = studentRepository.findByRollNo(roll);
                if (existing != null) {
                    existing.setName(name);
                    existing.setCourse(course);
                    existing.setCurrentSemester(sem);
                    student = studentRepository.save(existing);
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    student = studentRepository.save(newStudent(roll, name, course, sem));
                    result.setImported(result.getImported() + 1);
                }
            } else {
                student = studentRepository.save(newStudent(null, name, course, sem));
                result.setImported(result.getImported() + 1);
            }

            if (result.getPreview().size() < 10) {
                Map<String, String> line = new LinkedHashMap<>();
                line.put("rollNo", student.getRollNo());
                line.put("name", student.getName());
                line.put("course", student.getCourse());
                line.put("currentSemester", student.getCurrentSemester());
                line.put("missing", String.valueOf(missing));
                result.getPreview().add(line);
            }
            processed++;
        }

        if (processed == 0) {
            result.getNotes().add("No data rows found in the file.");
        }
        if (result.getMissingCells() > 0) {
            result.getNotes().add(result.getMissingCells() + " missing cell(s) were stored as null.");
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Readers
    // ---------------------------------------------------------------------

    private List<String[]> readExcel(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                short last = row.getLastCellNum();
                int width = Math.max(0, Math.min(last, 40));
                String[] cells = new String[width];
                for (int c = 0; c < width; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? null : formatter.formatCellValue(cell).trim();
                    cells[c] = (value == null || value.isEmpty()) ? null : value;
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private List<String[]> readPdf(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            for (String line : text.split("\\r\\n|\\n|\\r")) {
                if (line.trim().isEmpty()) {
                    rows.add(new String[0]);
                    continue;
                }
                rows.add(splitLoose(line));
            }
        }
        return rows;
    }

    private List<String[]> readDelimited(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null) return rows;
            char delimiter = detectDelimiter(firstLine);
            rows.add(splitDelimited(firstLine, delimiter));
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(splitDelimited(line, delimiter));
            }
        }
        return rows;
    }

    // ---------------------------------------------------------------------
    // Row -> field extraction
    // ---------------------------------------------------------------------

    /** Strict positional mapping used for Excel/CSV (cells are already atomic). */
    private Map<String, String> extractPositional(String[] cells, Map<String, Integer> mapping) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String field : new String[]{F_ROLL, F_NAME, F_COURSE, F_SEM}) {
            Integer idx = mapping.get(field);
            fields.put(field, (idx == null || idx >= cells.length) ? null : cells[idx]);
        }
        return fields;
    }

    /**
     * Token balancing used for PDF rows: a cell such as "akshay kumar" may arrive as
     * several tokens, so surplus tokens are merged into the name column; short rows
     * keep their leading columns and the rest becomes null.
     */
    private Map<String, String> extractByTokens(String[] cells, Map<String, Integer> mapping,
                                                int expected, int nameIdx) {
        List<String> tokens = new ArrayList<>();
        for (String cell : cells) {
            if (cell == null) continue;
            for (String part : cell.trim().split("\\s+")) {
                if (!part.isEmpty()) tokens.add(part);
            }
        }

        List<String> columns = new ArrayList<>();
        int absorbed = tokens.size() - expected;
        if (absorbed <= 0) {
            columns.addAll(tokens);
        } else {
            int nameAt = Math.min(nameIdx, Math.max(0, tokens.size() - 1));
            for (int i = 0; i < nameAt && i < tokens.size(); i++) columns.add(tokens.get(i));
            StringBuilder merged = new StringBuilder();
            for (int i = nameAt; i <= nameAt + absorbed && i < tokens.size(); i++) {
                if (merged.length() > 0) merged.append(' ');
                merged.append(tokens.get(i));
            }
            columns.add(merged.toString());
            for (int i = nameAt + absorbed + 1; i < tokens.size(); i++) columns.add(tokens.get(i));
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (String field : new String[]{F_ROLL, F_NAME, F_COURSE, F_SEM}) {
            Integer idx = mapping.get(field);
            fields.put(field, (idx == null || idx >= columns.size()) ? null : columns.get(idx));
        }
        repairShiftedSemester(fields, mapping);
        return fields;
    }

    /**
     * PDF text extraction collapses empty table cells, so values shift left:
     * "402 Asha S7" (empty course) would otherwise put "S7" into the course
     * column. When the semester column is empty but the course column holds a
     * semester-like value (S7, sem 3, 4th, ...), move it back to semester.
     */
    private void repairShiftedSemester(Map<String, String> fields, Map<String, Integer> mapping) {
        if (!mapping.containsKey(F_SEM)) return;
        String sem = fields.get(F_SEM);
        String course = fields.get(F_COURSE);
        if ((sem == null || sem.isBlank()) && course != null
                && course.matches("(?i)(sem[\\s-]*\\d{1,2}|s\\d{1,2}|\\d{1,2}(st|nd|rd|th)?)")) {
            fields.put(F_SEM, course);
            fields.put(F_COURSE, null);
        }
    }

    // ---------------------------------------------------------------------
    // Header mapping
    // ---------------------------------------------------------------------

    private Map<String, Integer> mapHeader(String[] cells) {
        Map<String, Integer> mapping = new HashMap<>();
        for (int i = 0; i < cells.length && i < 40; i++) {
            String field = aliasFor(cells[i]);
            if (field != null && !mapping.containsKey(field)) {
                mapping.put(field, i);
            }
        }
        return mapping;
    }

    /** True when the row looks like a column header ("roll no", "name", ...). */
    private boolean looksLikeHeader(String[] cells) {
        if (mapHeader(cells).size() >= 2) return true;
        List<String> tokens = new ArrayList<>();
        for (String cell : cells) {
            if (cell == null) continue;
            for (String part : cell.trim().split("\\s+")) {
                if (!part.isEmpty()) tokens.add(part);
            }
        }
        return mapHeader(tokens.toArray(new String[0])).size() >= 3;
    }

    private String aliasFor(String raw) {
        if (raw == null) return null;
        String header = raw.trim().toLowerCase().replaceAll("[.:]", "").replaceAll("\\s+", " ");
        switch (header) {
            case "roll no": case "roll number": case "rollno": case "roll": case "roll #":
            case "enrollment no": case "enrollment number": case "enrolment no":
            case "student id": case "student roll no": case "id":
                return F_ROLL;
            case "name": case "student name": case "full name": case "student":
                return F_NAME;
            case "course": case "branch": case "class": case "program": case "programme":
            case "stream": case "dept": case "department":
                return F_COURSE;
            case "semester": case "sem": case "current semester": case "term":
                return F_SEM;
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Splits a PDF line into table cells on 2+ spaces, tabs or pipes. */
    private String[] splitLoose(String line) {
        String trimmed = line.trim();
        String[] parts = trimmed.split("\\s{2,}|\\t|\\|");
        List<String> cells = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) cells.add(value);
        }
        return cells.toArray(new String[0]);
    }

    private char detectDelimiter(String line) {
        if (line.contains("\t")) return '\t';
        if (line.contains(";")) return ';';
        return ',';
    }

    private String[] splitDelimited(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                    else inQuotes = false;
                } else current.append(c);
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }

    private boolean isBlankRow(String[] cells) {
        if (cells == null || cells.length == 0) return true;
        for (String cell : cells) {
            if (cell != null && !cell.trim().isEmpty()) return false;
        }
        return true;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int countMissing(String... values) {
        int missing = 0;
        for (String value : values) if (value == null) missing++;
        return missing;
    }

    private Student newStudent(String roll, String name, String course, String sem) {
        Student student = new Student();
        student.setRollNo(roll);
        student.setName(name);
        student.setCourse(course);
        student.setCurrentSemester(sem);
        return student;
    }
}
