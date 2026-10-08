# PROJECT DETAILS — Exam Room and Seat Allocation Management System

> Complete technical documentation of the project as it is **actually implemented
> in this repository**. Everything below was verified against the source code.
> Anything not implemented is explicitly marked **Not Implemented** or **Planned**.

---

# 1. Project Overview

### What the project does
A web-based **Exam Room and Seat Allocation Management System**. It stores
students, rooms, batches/courses and exams, then automatically generates a
seating chart (the timetable allocation) for any exam and exports it as a PDF
that contains the student list (Roll Number, Name, Branch, Batch/Course) with
each student's assigned room and seat.

### Main purpose
Replace slow, error-prone manual exam seating work with a centralized,
automatic, conflict-free and malpractice-resistant seating arrangement.

### Who uses it
- **Exam coordinators / college admin staff** — upload student lists and exam
  schedules, generate and print seating charts.
- **Developers / students** — as a Spring Boot + JPA + PDFBox reference project.

### Main problems it solves
1. Manual seating takes hours and is hard to re-run when anything changes.
2. Same-course students sitting next to each other (copying risk).
3. Scheduling clashes (two papers at the same date/time for the same batch).
4. Missing or inconsistent student information on the final timetable PDF.

### Overall workflow (input → final PDF)
```
Student list (form / PDF / Excel / CSV)      Exam schedule (form / PDF / Excel / CSV)
              ↓                                          ↓
        Student table                                 Exam table
              └──────────────┬───────────────────────────┘
                             ↓
              Seating allocation (round-robin + desk mixing)
                             ↓
                 Conflict checks (students / rooms / slots)
                             ↓
              Seating chart on screen + PDF download
```

### Key features (all Implemented unless noted)
- Student data limited to **Roll Number, Name, Branch, Batch/Course** (no Division).
- Default batches **A–G** + custom batches through an **Add Course** button.
- Exam-data upload via **PDF / Excel / CSV** with extraction + validation.
- Student-list upload via PDF / Excel / CSV.
- Automatic seating chart generation (re-runnable, idempotent).
- Scheduling-conflict detection (schedule level + allocation level).
- Final PDF: colour-coded seating grid **and** a student roster table
  (Roll No | Name | Branch | Batch/Course | Room | Seat).
- Authentication: **Not Implemented** (the `Admin` entity exists as a Planned stub).

---

# 2. Technology Stack

| Technology | Where | What it is used for |
|---|---|---|
| **Java 17** (runs on JDK 17+) | `pom.xml` `<java.version>` | Main programming language |
| **Spring Boot 3.2.0** | `pom.xml` parent | Application framework (REST API, auto-configuration, embedded server) |
| **Spring Web** | `spring-boot-starter-web` | REST controllers, multipart file upload |
| **Spring Data JPA** | `spring-boot-starter-data-jpa` | ORM layer, repositories, transactions |
| **Hibernate** | via Spring Data JPA | JPA implementation, automatic DDL (`ddl-auto=update`) |
| **H2 (in-memory)** | `spring.datasource.url=jdbc:h2:mem:...` | Default database — zero-setup development/demo |
| **MySQL connector** | `mysql-connector-j` (runtime) | Optional production database |
| **PostgreSQL driver** | `postgresql` (runtime) | Optional production database |
| **Apache PDFBox 2.0.31** | `pdfbox` | PDF **generation** of the seating chart and PDF **text extraction** for imports |
| **Apache POI 5.2.5** | `poi-ooxml` | Reading Excel `.xlsx` / `.xls` uploads |
| **HTML + CSS + vanilla JS** | `src/main/resources/static/index.html` | Single-page admin frontend (no framework, no build step) |
| **Maven** | `pom.xml`, bundled in `.mvn-tool/apache-maven-3.9.9` | Build, dependency management, packaging |
| **Spring Boot test starter** | `spring-boot-starter-test` (test scope) | Test infrastructure (no unit test classes exist yet — see §24) |
| **Chrome DevTools Protocol** | `tools/test/cdp-test.mjs` | End-to-end UI test (drives real Chrome, no npm deps) |
| **Docker Compose** | `docker-compose.yml` | Optional local MySQL 8 instance |

Authentication: **Not Implemented** (no Spring Security dependency).

---

# 3. Complete Project Structure

```text
room-allocation-system/
├── README.md                     # Quick start + feature overview
├── PROJECT_DETAILS.md            # This document
├── todo.md                       # Requirement list + implementation status
├── plan.md                       # Original project plan
├── database.md                   # Database configuration guide
├── docker-compose.yml            # Optional MySQL 8 for production-like runs
├── pom.xml                       # Maven build + dependencies
├── .mvn-tool/
│   └── apache-maven-3.9.9/       # Bundled Maven (no global install needed)
├── src/
│   └── main/
│       ├── java/com/exam/allocation/
│       │   ├── RoomAllocationApplication.java   # Spring Boot entry point
│       │   ├── controller/                      # REST API layer
│       │   │   ├── AllocationController.java    # students, rooms, allocate, seats
│       │   │   ├── ApiExceptionHandler.java     # global JSON error handling
│       │   │   ├── BatchController.java         # course/batch management
│       │   │   ├── ChartController.java         # chart PDF download
│       │   │   ├── ExamController.java          # exam CRUD + conflicts
│       │   │   └── ImportController.java        # student + exam file upload
│       │   ├── model/                           # JPA entities (DB tables)
│       │   │   ├── Admin.java                   # Planned (auth not implemented)
│       │   │   ├── Batch.java                   # course/batch options (A–G + custom)
│       │   │   ├── Exam.java                    # exam event
│       │   │   ├── Room.java                    # exam room (grid layout)
│       │   │   ├── Seat.java                    # one physical seat assignment
│       │   │   └── Student.java                 # Roll No, Name, Branch, Batch
│       │   ├── repository/                      # Spring Data JPA repositories
│       │   │   ├── AdminRepository.java
│       │   │   ├── BatchRepository.java
│       │   │   ├── ExamRepository.java
│       │   │   ├── RoomRepository.java
│       │   │   ├── SeatRepository.java
│       │   │   └── StudentRepository.java
│       │   └── service/                         # Business logic
│       │       ├── AllocationService.java       # seating chart algorithm
│       │       ├── BatchService.java            # seeds A–G, Add Course CRUD
│       │       ├── ChartPdfService.java         # PDF rendering (chart + roster)
│       │       ├── ConflictService.java         # clash / double-booking detection
│       │       ├── ExamImportService.java       # exam PDF/Excel/CSV import
│       │       ├── FileTableReader.java         # shared PDF/Excel/CSV → rows
│       │       └── ImportService.java           # student PDF/Excel/CSV import
│       └── resources/
│           ├── application.properties           # DB, port, upload limits
│           └── static/
│               └── index.html                   # entire frontend (UI + JS)
├── target/                                      # Maven build output (generated)
│   └── room-allocation-system-0.0.1-SNAPSHOT.jar
└── tools/
    └── test/                                    # Test suite + fixtures
        ├── cdp-test.mjs                         # End-to-end UI test (CDP)
        ├── seed.sh                              # Seeds baseline data via curl
        ├── MakePdf.java                         # Regenerates test-students.pdf
        ├── MakeExamPdf.java                     # Regenerates test-exams.pdf
        ├── ExtractPdf.java                      # Debug: prints PDF text extraction
        ├── make_xlsx.py                         # Regenerates test-students.xlsx
        ├── make_exam_xlsx.py                    # Regenerates test-exams.xlsx
        └── test-students.csv / .xlsx / .pdf,
            test-exams.csv / .xlsx / .pdf        # Upload fixtures
```

> There is **no `src/test` folder** — the test suite lives in `tools/test/`
> (see §24). `target/` is generated and never edited by hand.

---

# 4. Important Folders

### `src`
All hand-written Java and web resources.
- `src/main/java` — production source code (controllers, services, models, repositories).
- `src/main/resources` — configuration (`application.properties`) and the frontend
  (`static/index.html`).
- `src/test` — **does not exist** (tests are in `tools/test/`).

### `target`
Maven's build output. Created by `mvn package`; contains compiled classes and the
runnable `room-allocation-system-0.0.1-SNAPSHOT.jar`. **Never edit files inside
`target/`** — it is deleted by `mvn clean`.

### `tools/test`
Test scripts, fixture generators and upload fixtures used by the seed script and
the CDP end-to-end test. Regenerate fixtures with `python make_xlsx.py`,
`java MakePdf.java test-students.pdf`, etc.

### Package folders (`src/main/java/com/exam/allocation/…`)
| Folder | Purpose |
|---|---|
| `controller` | REST endpoints; translate HTTP ↔ service calls; no business rules |
| `service` | All business logic: allocation algorithm, imports, PDF, conflicts, batches |
| `repository` | Spring Data JPA interfaces (SQL is generated by Hibernate) |
| `model` | JPA `@Entity` classes = database tables |
| `static` | The frontend single-page app (HTML/CSS/JS) |
| `resources` | Configuration files |

There is **no `uploads` folder** — uploaded files are parsed in memory
(`MultipartFile`) and never written to disk. There is **no `templates` folder**
(Thymeleaf is not used; the UI is a static page).

---

# 5. Main Important Files

| File | Location | Purpose | Important Functions |
|---|---|---|---|
| `index.html` | `src/main/resources/static/` | The whole frontend: tabs, forms, tables, chart rendering | `addStudent()`, `addBatch()`, `addExam()`, `uploadImport()`, `uploadExamImport()`, `generateAllocation()`, `renderChart()`, `downloadChartPdf()` |
| `AllocationService.java` | `service/` | Core seating algorithm | `generateAllocation(examId, roomIds)`, `fillDesk()`, `prepareSeats()`, `interleave()`, `matchesExam()` |
| `AllocationController.java` | `controller/` | REST for students/rooms/allocate/seats | `getAllStudents()`, `addStudent()`, `generateAllocation()`, `getAllSeats()` |
| `ExamImportService.java` | `service/` | Extracts exam rows from PDF/Excel/CSV, validates, stores | `importExams(file)`, `extract()`, `parseDateValue()`, `parseTimeValue()` |
| `ImportService.java` | `service/` | Extracts student rows from PDF/Excel/CSV | `importStudents(file)`, `mapHeader()`, `extractByTokens()` |
| `FileTableReader.java` | `service/` | Shared file → row reader used by both importers | `read(file)`, `readExcel()`, `readPdf()`, `readDelimited()` |
| `ConflictService.java` | `service/` | Scheduling clash detection | `findConflicts()`, `isSameSlot()`, `allocationWarnings()` |
| `ChartPdfService.java` | `service/` | Builds the final PDF (chart + student roster) | `buildChartPdf()`, `drawRoom()`, `drawSeat()`, `drawRoster()` |
| `BatchService.java` | `service/` | Course/batch management, seeds A–G at startup | `run()` (ApplicationRunner), `create()`, `ensureExists()`, `delete()` |
| `ExamController.java` | `controller/` | Exam CRUD + `GET /api/exams/conflicts` | `getAllExams()`, `addExam()`, `getConflicts()` |
| `ChartController.java` | `controller/` | `GET /api/chart/pdf?examId=` download | `downloadChart()` |
| `ImportController.java` | `controller/` | `POST /api/import/students`, `POST /api/import/exams` | `importStudents()`, `importExams()` |
| `Student.java` | `model/` | Student entity: `rollNo`, `name`, `branch`, `batch` | getters/setters only |
| `Seat.java` | `model/` | Seat ↔ Room, Seat ↔ Student, Seat ↔ Exam links | getters/setters only |
| `application.properties` | `resources/` | DB URL, H2 console, port, upload size limits | — |
| `pom.xml` | root | Dependencies (Spring, PDFBox, POI, DB drivers), Java 17 | — |
| `seed.sh` | `tools/test/` | Resets and seeds baseline data through the API | `delete_all()`, POST students/rooms/exam/allocate/import |
| `cdp-test.mjs` | `tools/test/` | End-to-end UI test (38 checks) driving Chrome via CDP | `check()`, `evaluate()`, per-tab test blocks |

**Data flow of the two biggest files**
- `ExamImportService`: file bytes in → validated `Exam` rows + conflict messages out.
- `ChartPdfService`: `Exam` + `List<Seat>` in → `byte[]` PDF out.

---

# 6. Application Architecture

```text
User (Admin)
   ↓  browser
Frontend / UI  —  src/main/resources/static/index.html  (fetch API)
   ↓  HTTP / JSON / multipart
Controller layer  —  /api/**  (Spring @RestController)
   ↓
Service layer  —  business logic (allocation, import, conflicts, PDF, batches)
   ↓
Repository layer  —  Spring Data JPA (interface + Hibernate)
   ↓
SQL Database  —  H2 in-memory (default) / MySQL / PostgreSQL
```

**Layer responsibilities**
- **Frontend**: tabbed SPA; renders forms/tables; calls the REST API with
  `fetch`; renders the seating grid client-side; downloads the PDF.
- **Controller**: request mapping, parameter binding, calling services, turning
  exceptions into JSON via `ApiExceptionHandler`.
- **Service**: all rules — who is eligible for an exam, how seats are filled,
  how files are parsed/validated, how conflicts are detected, how the PDF is drawn.
- **Repository**: `JpaRepository` interfaces; queries derived from method names
  (`findByRollNo`, `findByExamId`, `countByBatch`, …).
- **Database**: persistent (or in-memory) storage of all entities.

---

# 7. Database / SQL Connection

- **Database type**: H2 **in-memory** by default; MySQL and PostgreSQL supported
  (drivers bundled, `docker-compose.yml` provided).
- **Configuration file**: `src/main/resources/application.properties`
- **Connection URL**: `jdbc:h2:mem:room_allocation_db`
- **Driver**: `org.h2.Driver`
- **Username**: `sa` — **Password**: *(empty, development default only)*
- **ORM**: Spring Data JPA / Hibernate, dialect `H2Dialect`
- **DDL**: `spring.jpa.hibernate.ddl-auto=update` → Hibernate creates/alters the
  tables from the entities at startup.
- **H2 web console**: `http://localhost:8080/h2-console`

Safe example configuration (no real secrets — put production credentials in
environment variables, never in the repository):

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/room_allocation_db?createDatabaseIfNotExist=true
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:YOUR_PASSWORD}
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
```

**Repositories** (all extend `JpaRepository<Entity, Long>`):

| Repository | Custom methods |
|---|---|
| `StudentRepository` | `findByRollNo`, `findByBatch`, `countByBatch` |
| `RoomRepository` | `findByRoomNumber` |
| `SeatRepository` | `findByRoomId`, `findByExamId`, `findByAllocatedStudentId` |
| `ExamRepository` | — |
| `BatchRepository` | `findByNameIgnoreCase`, `findByOrderByNameAsc` |
| `AdminRepository` | `findByUsername` (Planned — unused) |

**Tables & relationships**: see §27 (ER diagram). Tables are created by Hibernate
from the `@Entity` classes: `student`, `room`, `seat`, `exam`, `batch`, `admin`.

---

# 8. Example SQL Code

Hibernate generates these tables automatically; equivalent hand-written SQL:

```sql
CREATE TABLE student (
    id        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    roll_no   VARCHAR(255) UNIQUE,
    name      VARCHAR(255),
    branch    VARCHAR(255),
    batch     VARCHAR(255)
);

CREATE TABLE batch (
    id     BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name   VARCHAR(255) NOT NULL UNIQUE,
    custom BOOLEAN NOT NULL
);

CREATE TABLE exam (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subject_name    VARCHAR(255),
    branch          VARCHAR(255),
    course          VARCHAR(255),
    exam_date       DATE,
    exam_time       TIME,
    application_id  VARCHAR(255)
);
```

```sql
-- INSERT
INSERT INTO student (roll_no, name, branch, batch) VALUES ('1', 'Akshay', 'CEC', 'E');
INSERT INTO batch (name, custom) VALUES ('H', TRUE);

-- SELECT
SELECT * FROM student WHERE batch = 'E';
SELECT * FROM exam WHERE exam_date = DATE '2026-11-02';

-- UPDATE
UPDATE student SET branch = 'MEC' WHERE roll_no = '1';

-- DELETE
DELETE FROM student WHERE roll_no = '1';

-- JOIN: students seated in a given exam with their room
SELECT s.roll_no, s.name, s.branch, s.batch, r.room_number, st.row_number, st.column_number
FROM seat st
JOIN student s ON s.id = st.allocated_student_id
JOIN room     r ON r.id = st.room_id
WHERE st.exam_id = 1
ORDER BY r.room_number, st.row_number, st.column_number;
```

---

# 9. How Application Data Reaches SQL

```text
User enters data (e.g. student form)
        ↓
index.html  →  addStudent() builds JSON
        ↓
HTTP POST /api/students   (fetch, Content-Type: application/json)
        ↓
AllocationController.addStudent()   — validates duplicate roll no
        ↓
StudentRepository.save()            — Spring Data JPA
        ↓
Hibernate executes INSERT           — inside a transaction
        ↓
SQL database (H2 / MySQL)
```

Every step:
1. The form collects Roll No, Name, Branch, Batch.
2. JS sends `{"rollNo":"1","name":"Akshay","branch":"CEC","batch":"E"}`.
3. The controller runs `ensureRollNoFree()` (business validation) and throws
   `IllegalArgumentException` on duplicates.
4. `save()` on the repository persists through Hibernate.
5. `ApiExceptionHandler` converts any exception to `{"message": "..."}` with
   HTTP 400/409 so the UI can show a readable toast.
6. Reads flow the same way in reverse (`findAll()` → JSON → `renderStudents()`).

File uploads use the same path with `multipart/form-data`
(`POST /api/import/students|exams`), except the service parses the file before
touching the repository.

---

# 10. Exam Data Upload

**Status: Implemented** (PDF, Excel, CSV).

### Endpoints & files
- `POST /api/import/exams` → `ImportController.importExams()` →
  `ExamImportService.importExams(file)`
- Shared file parsing: `FileTableReader.read()` (`.pdf` via PDFBox,
  `.xlsx/.xls` via POI, `.csv/.txt/.tsv` via delimiter detection).
- UI: **Import Data → Exam Data** tab (`uploadExamImport()` in `index.html`).

### Steps
1. **File validation** — empty file, missing file or unsupported extension
   (`.png`, …) → HTTP 400 with a readable message.
2. **Header detection** — first5 rows scanned for known column names
   (`subject`, `branch`, `batch/course`, `date`, `time`, `application id`, …).
   PDF "blob" rows are split into single-space tokens first. If no header is
   found, the default order *Subject, Branch, Batch/Course, Date, Time* is used
   and a note is added to the result.
3. **Data extraction** — date and time cells are located **by content**
   (`parseDateValue`, `parseTimeValue`), so shifted PDF tables still parse.
   A column literally headed `course` is ambiguous: batch-like values (A–G or a
   registered batch) stay the batch target, other values (CEC, CSE) become the
   branch.
4. **Validation** (rows are rejected and reported, never stored half-broken):
   - missing subject → `invalid` + note `"Row N: no subject/exam name found…"`
   - unparseable date (`99/99/2026`, empty) → `invalid` + note
   - unparseable time → exam stored **without** time + note
5. **Data storage** — `Exam` rows saved; an existing exam with the same
   subject+date is updated (`updated` counter) so re-imports are idempotent.
   Missing application IDs are auto-generated (`APP-<tag>-<yyyyMMdd>-<nnn>`).
   Batch-like course values are registered in the `batch` table so they are
   immediately usable by scheduling.
6. **Reaches the timetable generator** — the Seating Chart tab reads
   `GET /api/exams`; `POST /api/allocate` seats the eligible students.
7. **Conflicts** — after import, `ConflictService.findConflictsFor(new ids)`
   returns any new same-slot clashes, delivered in the result's `conflicts`
   array and shown in a red panel in the UI.
8. **Error handling** — all errors come back as `{"message": "..."}` (see §23).

### Supported date/time formats
`yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `d.M.yyyy`, `dd MMM yyyy`,
`MMM d yyyy`, `yyyyMMdd`, Excel serial dates — times: `10:00`, `10:00 AM`,
`09.30`, `0930`, `9 pm`.

### Fixture proof
`tools/test/test-exams.{csv,xlsx,pdf}` contain a valid pair that clashes
(Mathematics & Physics on 05/12/202610:00) and one invalid row
(`99/99/2026`) — the import reports `3 new /1 invalid /1 conflict`.

---

# 11. Student Data

Required fields (exactly, **no Division**):

```text
Roll Number:1
Name: Akshay
Branch: CEC
Batch: E
```

- **Entered**: Students tab form (Branch is a free-text input with a datalist of
  existing branches; Batch is a dropdown fed from `/api/batches`), inline edit
  rows, or an uploaded file (§10-style import).
- **Stored**: `student` table (`rollNo` unique, `name`, `branch`, `batch`).
  Missing cells are stored as `NULL` (imports tolerate incomplete files).
- **Retrieved**: `GET /api/students` → `StudentRepository.findAll()`.
- **Connected to exam/timetable data**: `AllocationService.matchesExam()`
  compares the exam's branch/batch targets against the student's branch/batch;
  each `Seat` row references the student (`allocated_student_id`) and the exam
  (`exam_id`), which is what the chart and the PDF are built from.
- **In the final PDF**: seat cells show Roll No, Name and `branch·batch`; the
  **STUDENT LIST** table shows Roll No | Name | Branch | Batch/Course | Room | Seat.

---

# 12. Course / Batch Management

**Status: Implemented.**

- **Defaults**: `A, B, C, D, E, F, G` — seeded by `BatchService` on startup
  (`ApplicationRunner`) whenever the `batch` table is empty, and re-checked
  lazily when `GET /api/batches` is called.
- **Add Course**: Courses tab → input + **Add Course** button →
  `POST /api/batches {"name":"H"}` → stored as `custom = true`.
  Duplicates and blank names are rejected with HTTP 400.
- **Storage**: `batch` table (`id`, `name` unique, `custom` flag).
- **Use in the timetable system**:
  - the student form's Batch dropdown,
  - the exam form's Batch/Course dropdown (`exam.course`),
  - student/exam imports auto-register unknown batch values (`ensureExists`),
  - `AllocationService` matches `exam.course` against `student.batch`
    (and `student.branch` as a fallback for imported "course" values).
- **Delete guard**: `DELETE /api/batches/{id}` is refused while any student still
  belongs to the batch (`countByBatch`).

---

# 13. Timetable Scheduling Logic

**Status: Implemented (allocation logic preserved from the original design).**

### Input data
`Exam` (branch + batch/course target, date, time), all `Student`s, all `Room`s
(rows × columns × students-per-desk).

### Constraints
1. Only students matching the exam's **branch** and **batch/course** are seated
   (blank / "All" = wildcard).
2. A student is never seated twice in the same exam run.
3. Never more students than available seats (excess reported, not silently dropped).
4. Students of the same **branch·batch group** are spread apart (anti-copying).
5. At shared desks, students from *different* groups are preferred.

### Scheduling algorithm — in plain language
> Put every eligible student into their branch·batch group, then take one
> student from each group in turn (round-robin) so the seating order mixes the
> groups. Walk the room desk by desk and fill each desk from the front of that
> order; when a desk seats several students, pick the next students from
> *different* groups whenever possible. Anything already seated for this exam is
> cleared first, so re-running simply regenerates the chart.

### Technically (`AllocationService.generateAllocation`)
1. Load exam + rooms + eligible students (`matchesExam`).
2. `groupingBy(groupKey)` → groups keyed by `branch·batch`
   → `interleave()` round-robins them alphabetically into one sequence.
3. Per room: `prepareSeats()` creates (or rebuilds) the seat grid
   `rows × columns × studentsPerDesk` for this exam and clears old assignments
   (idempotent).
4. Seats are consumed desk by desk; `fillDesk()` looks ahead in the queue and
   prefers a student whose group differs from the groups already at that desk;
   unused candidates return to the front of the queue in their original order.
5. `ConflictService.allocationWarnings()` post-checks the result for students
   or rooms booked in a same-slot exam.
6. Returns `AllocationResult { success, message, eligibleStudents, allocated,
   unallocated, roomsUsed, conflicts[] }`.

### Exam dates & times
Used for **conflict detection** (§ below), not for ordering the rooms — the
chart is always per exam.

### Conflict checking
- `GET /api/exams/conflicts` — pairs of exams with the **same date + time** and
  an **overlapping audience** (branch and batch filters are wildcards when blank).
- Allocation warnings — students seated in two same-slot exams (roll numbers
  listed) and rooms used by two same-slot exams.
- Reported after every `POST /api/allocate` in `conflicts[]` and in the UI
  (red panel under the chart / on the Exams tab).

### Output
The on-screen seat grid, the JSON result, and the PDF (§14).

```text
Exam Data ─┐
Student Data ─┼→ Eligibility filter → Group & interleave → Desk filling
Course/Batch Data ─┘                              ↓
                                        Conflict checking
                                                  ↓
                                          Generated timetable
                                                  ↓
                                        PDF generation
```

---

# 14. PDF Generation

**Status: Implemented.**

- **Library**: Apache **PDFBox** 2.0.31 (already a project dependency — no new
  library was added).
- **File**: `src/main/java/com/exam/allocation/service/ChartPdfService.java`.
- **Endpoint**: `GET /api/chart/pdf?examId=` (`ChartController`) returns the PDF
  bytes with a `Content-Disposition` filename such as
  `seating-chart-A-Data-Structures-2026-11-02.pdf`.
- **How data arrives**: controller loads `Exam` + its `Seat`s (each seat carries
  its `Student`, `Room`, row/column/desk position) and calls
  `buildChartPdf(exam, seats)`.

### Layout
1. **Header** — "SEATING CHART", subject, `Branch / Batch / Course / Date /
   Time / Application ID`, generation timestamp.
2. **Legend** — every `branch·batch` group with its colour and seated count.
3. **One section per room** — a landscape A4 grid (rows × columns); each cell is
   a desk: single student cells show Roll No (bold), Name and `branch·batch`
   on a group-coloured background; shared desks show one slot per position.
   Columns break across pages with repeated column labels.
4. **STUDENT LIST** (added for the requirements) — a roster table:

   | Roll No | Name | Branch | Batch / Course | Room | Seat |
   |---|---|---|---|---|---|
   |1 | Akshay | CEC | E |121 | R1 C1 P1 |

   Sorted by group then roll number (numeric-aware), with a repeated header row
   when it spans pages.
5. **Footer** — "Room Allocation System - generated <date>", page x of y.

### Simplified example of the actual code
```java
try (PDDocument document = new PDDocument()) {
    PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H)); // landscape A4
    document.addPage(page);
    PDPageContentStream cs = new PDPageContentStream(document, page);

    cs.beginText();
    cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
    cs.newLineAtOffset(MARGIN, y);
    cs.showText("SEATING CHART");
    cs.endText();

    // ... drawRoom() renders each seat cell, drawRoster() the student table ...

    cs.close();
    document.save(outputStream);   // returned as byte[]
}
```

---

# 15. Data Flow

```text
PDF / Excel / CSV input  (students or exams)
          ↓
    FileTableReader.read()        — PDFBox / POI / delimiter split
          ↓
    Header detection + column mapping
          ↓
    Row extraction + validation   — invalid rows rejected with reasons
          ↓
    SQL Database                  — student / exam / batch tables
          ↓
    Student + Course/Batch data   — eligibility filter (branch + batch)
          ↓
    Scheduling engine             — interleave + desk filling
          ↓
    Conflict checking             — same-slot clashes, double bookings
          ↓
    Timetable generated           — seat rows in DB + JSON for the UI
          ↓
    PDF generation                — ChartPdfService
          ↓
    Final PDF download            — /api/chart/pdf
```

Every stage is implemented; failures at extraction/validation stop that row
(with a message) but never corrupt existing data.

---

# 16. Important UI Tabs

| Tab/Page | Purpose | Main Actions | Important Backend/API |
|---|---|---|---|
| **Dashboard** | Overview counters + workflow hints | Jump buttons to each tab | `GET /api/students`, `/api/rooms`, `/api/exams`, `/api/seats`, `/api/batches` |
| **Students** | Manage student info (Roll No, Name, Branch, Batch) | Add, search, inline edit, delete | `GET/POST /api/students`, `PUT/DELETE /api/students/{id}` |
| **Rooms** | Manage exam rooms | Add (desk/rows/columns auto-calc), edit, delete | `GET/POST /api/rooms`, `PUT/DELETE /api/rooms/{id}` |
| **Courses** | Course/batch management (defaults A–G) | **Add Course**, delete custom batches (guard when in use) | `GET/POST /api/batches`, `DELETE /api/batches/{id}` |
| **Exams** | Exam list + scheduling conflicts | Create/edit/delete exams, view clash panel | `GET/POST /api/exams`, `PUT/DELETE /api/exams/{id}`, `GET /api/exams/conflicts` |
| **Seating Chart** | Generate and view the timetable | Pick exam, generate, see stats/conflicts/unseated chips, download PDF, print | `POST /api/allocate`, `GET /api/seats?examId=`, `GET /api/chart/pdf?examId=` |
| **Import Data** | Upload student lists **and exam data** | Mode switch (Student List / Exam Data), upload, preview, template download | `POST /api/import/students`, `POST /api/import/exams` |
| Settings / Admin / Login | — | **Not Implemented** (Admin entity is a Planned stub) | — |

---

# 17. API Endpoints

All responses are JSON except the PDF download. Errors return
`{"message": "..."}` with HTTP 400 (validation) / 409 (data conflict).

| Method | Endpoint | Purpose | Request | Response |
|---|---|---|---|---|
| GET | `/api/students` | List students | — | Student array |
| POST | `/api/students` | Add student | `{"rollNo","name","branch","batch"}` | Created student (400 on duplicate roll) |
| PUT | `/api/students/{id}` | Update student | same JSON | Updated student |
| DELETE | `/api/students/{id}` | Delete student (clears seat refs) | — | `{"deleted":true,"clearedSeats":n}` |
| GET | `/api/rooms` | List rooms | — | Room array |
| POST | `/api/rooms` | Add room | `{"roomNumber","rowsCount","columnsCount","studentsPerDesk","capacity"}` | Created room (400 on duplicate number) |
| PUT | `/api/rooms/{id}` | Update room | same JSON | Updated room |
| DELETE | `/api/rooms/{id}` | Delete room + its seats | — | `{"deleted":true}` |
| GET | `/api/batches` | List batches (defaults first) | — | Batch array |
| POST | `/api/batches` | **Add Course** | `{"name":"H"}` | Created batch (400 if blank/duplicate) |
| DELETE | `/api/batches/{id}` | Delete batch | — | `{"deleted":true}` (400 while students use it) |
| GET | `/api/exams` | List exams (by date) | — | Exam array |
| POST | `/api/exams` | Create exam | `{"subjectName","branch","course","examDate","examTime","applicationId"}` | Created exam |
| PUT | `/api/exams/{id}` | Update exam | same JSON | Updated exam |
| DELETE | `/api/exams/{id}` | Delete exam + its chart | — | `{"deleted":true}` |
| GET | `/api/exams/conflicts` | Detect same-slot clashes | — | `Conflict[] {type, examId, otherExamId, message}` |
| POST | `/api/allocate` | Generate seating chart | `{"examId":1}` (optional; latest exam otherwise) | `AllocationResult {success, message, counts, conflicts[]}` |
| GET | `/api/seats?examId=` | Seats of one exam (all if omitted) | — | Seat array with `allocatedStudent` |
| GET | `/api/chart/pdf?examId=` | Download the chart PDF | — | `application/pdf` bytes |
| POST | `/api/import/students` | Import student list | multipart `file` (.pdf/.xlsx/.xls/.csv/.txt) | `{imported, updated, skipped, missingCells, preview[], notes[]}` |
| POST | `/api/import/exams` | Import exam data | multipart `file` (same formats) | `{imported, updated, invalid, skipped, preview[], notes[], conflicts[]}` |

**Authentication/authorization**: none (single-user admin tool; login is
**Not Implemented**). **Validation**: performed in controllers/services as noted
(duplicate roll no / room no / batch name, required subject + date, file type
checks) plus Spring's `MultipartException` handling for missing/oversized files
(max 20 MB per file, configured in `application.properties`).

---

# 18. Important Classes / Components

| Component | Type | Role & interaction |
|---|---|---|
| `AllocationController` | REST controller | Entry point for students/rooms/allocate/seats; delegates chart building to `AllocationService` |
| `ExamController` | REST controller | Exam CRUD; exposes `ConflictService.findConflicts()` |
| `BatchController` | REST controller | Batch listing/creation/deletion via `BatchService` |
| `ImportController` | REST controller | Two upload endpoints → `ImportService` / `ExamImportService` |
| `ChartController` | REST controller | Loads exam + seats → `ChartPdfService.buildChartPdf()` |
| `ApiExceptionHandler` | `@RestControllerAdvice` | Maps `IllegalArgumentException`→400, `DataIntegrityViolationException`→409, multipart errors→400 |
| `AllocationService` | Service | The seating algorithm (§13); uses `ConflictService` for post-checks |
| `ImportService` | Service | Student file import: header mapping, token balancing for PDF, branch/batch routing, batch auto-registration |
| `ExamImportService` | Service | Exam file import: extraction, validation, idempotent upsert, application-ID generation, conflict reporting |
| `FileTableReader` | `@Component` | Shared file → `List<String[]>` reader (PDF/Excel/CSV) — avoids duplicated parsing code |
| `ConflictService` | Service | `isSameSlot()` (date+time+audience), `findConflicts()`, `allocationWarnings()` |
| `BatchService` | Service (`ApplicationRunner`) | Seeds A–G at startup; create/ensure/delete with usage guard |
| `ChartPdfService` | Service | Pure PDF drawing (grid, legend, roster, footers) — no DB access |
| `Student/Exam/Room/Seat/Batch/Admin` | JPA entities | Database tables; `Seat` is the join point of room + student + exam |
| `*Repository` | Spring Data | All SQL generated by Hibernate |

---

# 19. Configuration Files

| File | Controls | Safe to change? | Secrets? |
|---|---|---|---|
| `src/main/resources/application.properties` | App name, **database URL/driver/user**, H2 console, Hibernate DDL mode, **server port** (`8080`), multipart upload limits (20 MB), error message exposure | Yes — DB settings and port live here | DB password here is **empty (H2 dev default)**; production credentials must come from environment variables |
| `pom.xml` | Dependencies, Java version, Spring Boot version, packaging | Yes (keep versions consistent) | No |
| `docker-compose.yml` | Local MySQL 8 service, port3006, database name `room_allocation_db` | Yes — **change the local root password before any shared use** | Contains a local-dev placeholder password (`root`); replace via environment variable in real deployments |
| `.vscode/settings.json` | Editor behaviour only | Yes | No |
| `tools/test/seed.sh`, `cdp-test.mjs` | Test endpoints/paths | Yes (ports must match the running instance) | No |

There is **no `.env`**, no `application.yml`, no `package.json` (the frontend has
no build tooling).

---

# 20. Build and Run Process

```text
1. Install prerequisites : JDK17+ (project builds on newer JDKs too), Node18+
                           (only for the UI test), Python3 (only to regenerate fixtures)
2. Configure database    : default H2 needs nothing; optional MySQL: docker-compose up -d
3. Environment variables : none required for the default setup
4. Install dependencies   : resolved automatically by Maven on first build
5. Build project          : mvn clean package -DskipTests
6. Start application      : java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar
7. Open application       : http://localhost:8080
```

Exact commands (bundled Maven, no global install):

```bash
# build
./.mvn-tool/apache-maven-3.9.9/bin/mvn clean package -DskipTests

# run (default port8080, H2 in-memory)
java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar

# run on another port (used by the test suite)
java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar --server.port=8081

# tests (app must run on8081)
bash tools/test/seed.sh http://localhost:8081/api
node tools/test/cdp-test.mjs
```

---

# 21. Development vs Production

| Aspect | Development | Production |
|---|---|---|
| Database | H2 in-memory (`jdbc:h2:mem:...`) — data lost on restart | MySQL/PostgreSQL via `docker-compose.yml` or a real server; set `spring.datasource.*` accordingly |
| Credentials | Empty H2 password (local only) | Pass `DB_USERNAME`/`DB_PASSWORD` as environment variables; never commit them |
| Build | `mvn package` on demand, `show-sql=true` for debugging | `mvn clean package`, set `spring.jpa.show-sql=false` |
| Logging | SQL printed to console | Reduce logging; send to a log store |
| Port | `8080`/`8081` behind no proxy | Put behind a reverse proxy with TLS |
| File storage | Uploads parsed in memory, never persisted | Same — but add virus scanning/quota if exposed publicly |
| Security | No auth (single user) | Add authentication/authorization (**Planned**, see §22) |

---

# 22. Security

- **Password handling**: no user login exists (**Not Implemented**). The
  `Admin` entity has a `password` column reserved for a future
  authentication feature; nothing writes to it today.
- **Database credentials**: development H2 uses an empty password; production
  credentials must be supplied via environment variables. **No real password,
  API key or token appears anywhere in this repository or in this document.**
- **File upload security**: only `.pdf/.xlsx/.xls/.csv/.txt/.tsv` accepted
  (extension check → HTTP 400 otherwise), size limited by
  `spring.servlet.multipart.max-file-size=20MB`, files are parsed in memory and
  **never stored on disk**, parsing is done by PDFBox/POI (no shell-out).
- **Authentication/authorization**: **Not Implemented** — the app assumes a
  trusted internal network. Recommended before public deployment.
- **Input validation**: duplicate roll no / room no / batch name rejected;
  exam rows require a subject and a parseable date; all values are sent as
  parameters/JPA entities — no string-built SQL.
- **SQL injection prevention**: Spring Data JPA/Hibernate parameter binding for
  every query.
- **Sensitive data handling**: uploads contain student data only; nothing is
  logged to third parties.

---

# 23. Error Handling

| Where | What | Behaviour |
|---|---|---|
| `ApiExceptionHandler` | `IllegalArgumentException` | HTTP400 `{"message": "Roll No "900" already exists."}` → UI toast |
| `ApiExceptionHandler` | `DataIntegrityViolationException` | HTTP409 duplicate/invalid data |
| `ApiExceptionHandler` | Missing/oversized multipart file | HTTP400 "A file is required…" |
| Import services | Unsupported extension / empty file | HTTP400 with the supported list |
| `ExamImportService` | Row without subject or with bad date | Row rejected, counted in `invalid`, reason in `notes` (never stored) |
| Exam import | Unparseable time | Exam stored without a time + note |
| `AllocationService` | No matching students / no rooms | `success=false` with a readable message (no exception) |
| `ConflictService` | Same-slot exams, double-booked students/rooms | Warnings in `conflicts[]` — allocation still succeeds |
| `BatchService` | Deleting a batch in use | HTTP400 "Cannot delete … N student(s) still belong…" |
| `ChartPdfService` | No seats for the exam | HTTP400 "No seating chart exists … Generate the allocation first." |
| Frontend `api()` | Any non-2xx | Extracts `message` from the JSON and shows a red toast; page never crashes |

**Troubleshooting examples**
- *"No students match this exam (batch/course: E, branch: CEC)"* → no student
  has that branch/batch; fix the exam target or the student data.
- *"N student(s) could not be seated"* → add rooms / rows / columns.
- *"Unsupported file type"* → convert the file to `.pdf`, `.xlsx` or `.csv`.

---

# 24. Testing

**Status: Implemented as an end-to-end suite in `tools/test/`** (no JUnit classes
exist yet — `spring-boot-starter-test` is available if unit tests are added).

| Test | File | Covers |
|---|---|---|
| UI end-to-end (38 checks) | `tools/test/cdp-test.mjs` | Dashboard stats, student CRUD + Enter navigation, room desk/capacity math, **batches tab (A–G + Add Course + delete)**, exam creation + auto app-ID, **allocation + shared desks + legend**, **student file import**, **exam file import incl. invalid-row rejection and clash reporting**, **double-booking warnings**, PDF reachability, zero page errors |
| Data seeding | `tools/test/seed.sh` | Resets data, posts students (branch/batch), rooms, exam, baseline allocation, student fixture imports |
| Fixture generators | `make_xlsx.py`, `make_exam_xlsx.py`, `MakePdf.java`, `MakeExamPdf.java` | Build the `.xlsx`/`.pdf` upload fixtures (incl. missing cells & invalid rows) |
| PDF extraction debug | `ExtractPdf.java` | Prints exactly what the importer sees |

Run:
```bash
# terminal1 — app
java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar --server.port=8081
# terminal2 — seed + Chrome + test
bash tools/test/seed.sh http://localhost:8081/api
chrome.exe --headless=new --remote-debugging-port=9333 --user-data-dir=%TEMP%/chrome-test http://localhost:8081/
node tools/test/cdp-test.mjs
```

Latest result: **ALL UI TESTS PASSED**, no page errors.

---

# 25. Common Developer Tasks

| Task | Files to change |
|---|---|
| Add a new batch by default | `BatchService.DEFAULT_BATCHES` |
| Add a new student field | `model/Student.java`, `AllocationController` (add/update), `ImportService` (header alias + extraction), `index.html` (form, table, edit row, payload), optionally `ChartPdfService` roster |
| Modify student fields' meaning | `model/Student.java` + everything above |
| Add a new exam field | `model/Exam.java`, `ExamController.updateExam()`, `ExamImportService` (alias + extraction), `index.html` exams tab, `ChartPdfService.drawHeader()` if it belongs in the PDF |
| Add a new exam manually | UI: Exams tab; data: `POST /api/exams`; bulk: extend an import fixture |
| Change timetable rules | `AllocationService` — `matchesExam()`, `interleave()`, `fillDesk()` |
| Change PDF layout | `ChartPdfService` (grid: `drawRoom/drawSeat`; roster: `drawRoster`; header: `drawHeader`) |
| Add a new API endpoint | New/existing `controller/*.java`; register errors in `ApiExceptionHandler` |
| Add a database field | Entity → restart (Hibernate `ddl-auto=update` alters the table) → repositories/controllers/UI as above → update §8/§27 docs |
| Change DB configuration | `src/main/resources/application.properties` (+ `database.md`) |
| Add a new UI tab | `index.html`: tab button, `section-view` div, entry in `loaders` map, render/load functions |
| Modify conflict rules | `ConflictService` (`isSameSlot`, `audiencesOverlap`, `allocationWarnings`) |
| Regenerate fixtures | `python make_xlsx.py`, `python make_exam_xlsx.py`, `java MakePdf.java test-students.pdf`, `java MakeExamPdf.java test-exams.pdf` (needs PDFBox on the classpath) |

---

# 26. Example Complete Workflow

```text
Student:
Roll No:1
Name: Akshay
Branch: CEC
Batch: E
```

```text
Student Form (Students tab)
     ↓  POST /api/students {"rollNo":"1","name":"Akshay","branch":"CEC","batch":"E"}
Controller validation (unique roll no)
     ↓  StudentRepository.save()
Database:  INSERT INTO student (roll_no, name, branch, batch) VALUES ('1','Akshay','CEC','E')
     ↓
Seating (Seating Chart tab → exam "Data Structures", batch E,02.11.202610:00)
     ↓  POST /api/allocate {"examId":1}
matchesExam(): exam.batch "E" == student.batch "E" → eligible
     ↓  interleave + fillDesk
Seat row:  seat(row=1, column=2, position=1, student=Akshay, exam=Data Structures)
     ↓
On-screen timetable grid + conflict checks
     ↓  GET /api/chart/pdf?examId=1
Final PDF
```

Expected final PDF content for this student:

```text
SEATING CHART
Data Structures
Branch: All   Batch/Course: E   Date: 2026-11-02   Time:10:00   Application ID: ...

[desk cell] 1 / Akshay / CEC·E     (coloured by group)

STUDENT LIST
Roll No | Name   | Branch | Batch / Course | Room | Seat
1       | Akshay | CEC    | E              |121   | R1 C2 P1
```

---

# 27. Database Relationship Diagram

```text
STUDENT                         EXAM
---------                       ---------
id (PK)                         id (PK)
roll_no (UNIQUE)                subject_name
name                            branch            ← target branch (nullable = all)
batch   ─┐                      course            ← target batch/course (nullable = all)
branch   │                      exam_date
         │                      exam_time
         │                      application_id
         │                            │
         │                            │
         │        SEAT                │
         │        ---------           │
         │        id (PK)             │
         │        row_number          │
         │        column_number       │
         │        desk_position       │
         └─────── allocated_student_id (FK → student.id)
                 exam_id (FK → exam.id)
                 room_id (FK → room.id)
                        │
ROOM                      │
---------                 │
id (PK) ──────────────────┘
room_number (UNIQUE)
capacity
rows_count
columns_count
students_per_desk
room_type (discriminator)

BATCH                      ADMIN (Planned / unused)
------                     ------------------------
id (PK)                    id (PK)
name (UNIQUE)              username (UNIQUE)
custom (boolean)           password
```

Relationships:
- `Seat → Student` **many-to-one** (a student sits in many exams over time).
- `Seat → Room` **many-to-one**; `Room → Seat` **one-to-many** (cascade delete).
- `Seat → Exam` **many-to-one**; deleting an exam removes its seats.
- `Student.batch` stores the **batch name string** (no FK) — batches are soft
  referenced so imported/unknown values never fail to save; `BatchService`
  registers them. This is intentional and documented.

---

# 28. File/Folder Quick Reference

| Folder/File | What it is | When to modify it |
|---|---|---|
| `src/main/java/.../controller/` | REST API endpoints | Adding/changing endpoints |
| `src/main/java/.../service/AllocationService.java` | Seating algorithm | Changing timetable rules |
| `src/main/java/.../service/ExamImportService.java` | Exam upload parsing/validation | Supporting new exam-file formats |
| `src/main/java/.../service/ImportService.java` | Student upload parsing | Supporting new student-file formats |
| `src/main/java/.../service/FileTableReader.java` | Shared PDF/Excel/CSV reader | File-type support for both importers |
| `src/main/java/.../service/ChartPdfService.java` | Final PDF rendering | PDF layout/content changes |
| `src/main/java/.../service/ConflictService.java` | Clash/double-booking rules | Conflict rules |
| `src/main/java/.../service/BatchService.java` | Batch seeding + CRUD rules | Default batches / batch rules |
| `src/main/java/.../model/` | Entities = DB tables | Schema changes |
| `src/main/java/.../repository/` | JPA queries | New queries |
| `src/main/resources/application.properties` | DB, port, upload limits | Environment configuration |
| `src/main/resources/static/index.html` | Entire frontend | Any UI change |
| `pom.xml` | Dependencies & build | Adding libraries |
| `docker-compose.yml` | Local MySQL | Production-like database |
| `tools/test/` | Tests, fixtures, seed script | Test scenarios / fixtures |
| `README.md` | Quick start | Run instructions change |
| `PROJECT_DETAILS.md` | This document | Any architecture change |
| `database.md` | Database guide | DB configuration changes |
| `todo.md` | Requirement status | When requirements are implemented |
| `target/` | Build output | Never edit |
| `plan.md` | Original project plan | Historical reference |

---

# 29. MBA / Project Presentation Understanding

**Business problem.** Colleges schedule dozens of exams; manual seating charts
take hours of staff time, are error-prone (duplicate students, wrong rooms,
clashing papers) and are hard to revise when a batch or room changes. The
resulting charts often omit information invigilators need (branch, batch).

**Proposed solution.** A centralized web application where staff enter or upload
students and exam schedules, press one button, and get an automatic,
conflict-checked seating timetable that can be printed as a professional PDF.

**Target users.** Exam coordinators, department admins, and examination office
staff (no technical skills required — a browser is enough).

**Main workflow.**
1. Student data in (form or file upload) →2. Rooms & batches defined →
3. Exam schedule uploaded (PDF/Excel) →4. One click generates the seating chart →
5. Conflicts highlighted →6. PDF downloaded and printed.

**Operational benefits.**
- Seating work that took hours is done in seconds and can be re-run any time.
- Consistency: one source of truth for students, batches, exams and seats.
- Error reduction: duplicate roll numbers, invalid dates and scheduling clashes
  are detected automatically instead of on exam day.
- Auditability: every student's room and seat is recorded and printable.

**Automation benefits.** Round-robin spreading and desk-level mixing automate
the anti-copying rules that staff previously applied by hand; conflict checks
run on every generation instead of relying on memory.

**Data management.** Structured tables (students, batches, exams, rooms, seats)
replace spreadsheets and paper; uploads are validated before anything is stored.

**Scalability.** The same code runs against H2 for demos and MySQL/PostgreSQL
(`docker-compose.yml` provided) for production; rooms and students scale with
the database, and the PDF paginates automatically.

**Efficiency improvements.** Seconds vs. hours per exam; re-generation after
last-minute changes is a single click; no specialised training needed.

**Error reduction.** Validation at every entry point (uniqueness, required
fields, date parsing) + explicit scheduling-conflict reporting.

**Future possibilities (Planned / Not Implemented).** Login & roles for staff,
multiple exam sessions per day, hall-wise invigilator assignment, drag-and-drop
seat adjustments, exporting to Excel, notifications, and reporting dashboards.

---

# 30. Project Summary

- **What is this project?** A Spring Boot web application that manages students,
  rooms, batches and exams and automatically generates exam seating timetables
  as PDFs.
- **What problem does it solve?** Manual, slow and error-prone exam seating and
  timetable planning — including scheduling clashes and missing student details.
- **How does data enter the system?** Through the browser UI (forms and tabs) or
  by uploading PDF/Excel/CSV files for students and exam schedules.
- **Where is the data stored?** In a SQL database via Spring Data JPA — H2
  in-memory by default, MySQL/PostgreSQL for production.
- **How is the timetable generated?** `AllocationService` filters students by
  the exam's branch/batch target, interleaves them round-robin across
  `branch·batch` groups, fills each room desk preferring different groups, then
  runs conflict checks.
- **How is it converted to PDF?** `ChartPdfService` (Apache PDFBox) draws a
  landscape A4 colour-coded seating grid plus the **Student List** table
  (Roll No | Name | Branch | Batch/Course | Room | Seat), served by
  `GET /api/chart/pdf?examId=`.
- **Most important files?** `AllocationService.java`, `ChartPdfService.java`,
  `ExamImportService.java`, `ConflictService.java`, `static/index.html`.
- **Most important folders?** `src/main/java/com/exam/allocation/{service,controller,model,repository}`
  and `src/main/resources/static`.
- **How does the whole system work?** Files/forms → REST API → services → JPA
  repositories → SQL database → allocation + conflict services → on-screen chart
  → PDF download.

---

*Keep this document updated whenever the architecture or major features change.*
