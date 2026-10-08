# Exam Room and Seat Allocation Management System

A web application that automates exam seating: it stores students (Roll No, Name,
Branch, Batch/Course), rooms and exams, generates a conflict-free seating chart
for every exam, detects scheduling clashes, and exports the final timetable as a
PDF that includes the student list with Roll Number, Name, Branch and Batch.

## Key features

- **Student management** — exactly four fields per student: Roll Number, Name,
  Branch, Batch/Course (no Division field).
- **Course/Batch management** — default batches `A, B, C, D, E, F, G` seeded
  automatically, plus an **Add Course** button for custom batches that become
  immediately available to the student form, exam form and scheduling.
- **Exam-data upload** — import exam schedules from **PDF, Excel (.xlsx/.xls) or
  CSV**; subject, branch, batch/course, date, time and application ID are
  extracted, validated (rows without a subject or a parseable date are rejected
  and reported) and stored in the database for timetable generation.
- **Student-list upload** — import student lists from the same formats.
- **Seating allocation** — round-robin interleaving across branch·batch groups,
  subject-mixing at shared desks, re-runnable and idempotent.
- **Conflict detection** — same date + time + audience exam clashes, plus
  warnings when a student or room is double-booked by the generated chart.
- **PDF export** — landscape A4 seating chart (colour-coded by branch·batch)
  with a full **Student List** table: Roll No | Name | Branch | Batch/Course |
  Room | Seat.
- **Single-page admin UI** — Dashboard, Students, Rooms, Courses, Exams,
  Seating Chart and Import tabs.

## Technology

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 17+, Spring Boot 3.2 (Web, Data JPA) |
| Database  | H2 in-memory by default (MySQL/PostgreSQL drivers included, `docker-compose.yml` provided) |
| PDF       | Apache PDFBox 2.0.31 (generation **and** text extraction for import) |
| Excel     | Apache POI 5.2.5 |
| Frontend  | Single-page HTML/CSS/vanilla JS served from `src/main/resources/static/index.html` |
| Build     | Maven (`./.mvn-tool/apache-maven-3.9.9/bin/mvn` bundled) |

## Run it

```bash
#1. Build
./.mvn-tool/apache-maven-3.9.9/bin/mvn clean package -DskipTests

#2. Start (H2 in-memory database, port 8080 by default)
java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar

#3. Open the UI
#    http://localhost:8080
#    H2 console: http://localhost:8080/h2-console  (JDBC URL jdbc:h2:mem:room_allocation_db, user sa, empty password)
```

To use MySQL instead, start `docker-compose up -d` and switch the
`spring.datasource.*` values in `src/main/resources/application.properties`
(see `database.md`).

## Typical workflow

1. **Students** tab — add students (Roll No, Name, Branch, Batch) or import a list.
2. **Rooms** tab — add exam rooms (rows × columns, students per desk).
3. **Courses** tab — keep the defaults A–G or add custom batches.
4. **Import Data** tab — upload the exam schedule as PDF/Excel/CSV (or create exams manually).
5. **Exams** tab — review exams and any reported scheduling conflicts.
6. **Seating Chart** tab — generate the chart, check conflict warnings, download the PDF.

## Tests

```bash
# Seed baseline data (app must be running on :8081)
bash tools/test/seed.sh http://localhost:8081/api

# End-to-end UI test through Chrome DevTools Protocol
# (start Chrome first: chrome.exe --headless=new --remote-debugging-port=9333 --user-data-dir=%TEMP%/chrome-test http://localhost:8081/)
node tools/test/cdp-test.mjs
```

Fixture generators live next to the tests: `make_xlsx.py`, `make_exam_xlsx.py`,
`MakePdf.java`, `MakeExamPdf.java`.

## Documentation

- `PROJECT_DETAILS.md` — full architecture, database, API, scheduling, PDF and
  developer guide (30 sections).
- `database.md` — database configuration and entity/table mapping.
- `plan.md` — original project plan.
- `todo.md` — implementation status of the requirements.
