# TODO — Implementation Status

Status legend: `[x]` implemented **and verified** · `[ ]` pending/not implemented

This file replaces the original requirement list with the actual implementation
status; each section below restates its requirement and what was built.

Last verified: build `room-allocation-system-0.0.1-SNAPSHOT.jar` — application
starts, full API test pass + `tools/test/cdp-test.mjs` **ALL UI TESTS PASSED**
(39 checks, no page errors).

---

## 1. Student Information  `[x] Completed`

- [x] Store and display only **Roll Number, Name, Branch, Batch/Course**
      (`Student` entity: `rollNo`, `name`, `branch`, `batch`)
- [x] **No Division field** anywhere (entity, UI, PDF)
- [x] Example works end-to-end: `1 | Akshay | CEC | E`
- [x] Verified: create/update/delete via API and UI; all three upload formats
      (PDF/Excel/CSV) import into branch/batch correctly; missing cells stored as null

## 2. Course/Batch Management  `[x] Completed`

- [x] Default batches **A, B, C, D, E, F, G** seeded automatically on startup
      (`BatchService` ApplicationRunner)
- [x] **"Add Course"** button on the new **Courses** tab creates custom batches
      (`POST /api/batches`)
- [x] New batches are immediately available in the student form, the exam form
      and the timetable/seating generation (`exam.course` → `student.batch` matching)
- [x] Delete guard: a batch in use by students cannot be deleted (HTTP 400)
- [x] Verified via API tests and the UI test (add `Z` → visible in both dropdowns → delete)

## 3. Exam Data Upload  `[x] Completed`

- [x] Upload exam data as **PDF** and **Excel** (.xlsx/.xls) and **CSV**
      (`POST /api/import/exams`, Import Data → Exam Data tab)
- [x] Extracts subject, branch, batch/course, date, time and application ID
      (content-based date/time location, so shifted PDF tables still parse)
- [x] Validates extracted data: rows without a subject or with an unparseable
      date are rejected and reported (`invalid` counter + per-row reasons);
      unparseable times are stored as null with a note
- [x] Handles invalid/incorrectly formatted files: unsupported extension →
      HTTP 400 listing supported formats; empty/missing file → HTTP 400;
      bad rows are never stored
- [x] Extracted data flows into the existing database and is used by timetable
      generation (imported exams appear in the Seating Chart dropdown)
- [x] Idempotent: re-importing the same file updates instead of duplicating
- [x] Batch values found in files are auto-registered so they can be scheduled
- [x] Verified with CSV, XLSX and PDF fixtures including one deliberately
      invalid row (`3 new / 1 invalid / 1 clash` reported)

## 4. Timetable / Scheduling  `[x] Completed`

- [x] Existing allocation logic preserved (round-robin interleave + desk mixing +
      idempotent seat grid); only the audience matching was extended from
      course/semester to **branch + batch/course**
- [x] Student data, course/batch data and uploaded exam data are integrated
      (`matchesExam` = exam.branch + exam.course vs student.branch/student.batch)
- [x] Scheduling conflict detection:
  - [x] `GET /api/exams/conflicts` — same date + time + overlapping audience
  - [x] Conflicts listed in every exam-import result
  - [x] Allocation warnings for double-booked students (roll numbers listed)
        and double-booked rooms
  - [x] Both surfaced in the UI (Exams tab + Seating Chart tab)
- [x] Generated timetable uses correct student/course/exam info (verified on
      screen and in the PDF)

## 5. Generated PDF  `[x] Completed`

- [x] Uses the existing PDF library (**Apache PDFBox** — no new dependency)
- [x] `ChartPdfService` draws: header with exam details (branch, batch/course,
      date, time, application ID), colour-coded seating grid, and a **STUDENT
      LIST** table with **Roll No | Name | Branch | Batch/Course | Room | Seat**
- [x] **Every row and column of the room grid is present in the PDF** (verified:
      a 3×8 room renders R1–R3, C1–C8 labels, all desks, shared-desk slots)
- [x] **No Division** in the PDF
- [x] Verified by extracting the generated PDF text (`/api/chart/pdf` → header +
      roster rows present)

## 6. Database  `[x] Completed`

- [x] Connection verified (H2 in-memory by default; MySQL/PostgreSQL drivers +
      `docker-compose.yml` ready; configuration documented in `database.md`)
- [x] Entities/models verified: `Student`, `Batch`, `Exam`, `Room`, `Seat` (+ `Admin` stub)
- [x] Repositories verified (`findByRollNo`, `countByBatch`, `findByExamId`, …)
- [x] Services verified (allocation, imports, conflicts, batches, PDF)
- [x] Tables/relationships verified (student ↔ seat ↔ room ↔ exam; batch names
      referenced by students/exams — see `PROJECT_DETAILS.md` §27)
- [x] Student, batch/course, exam and timetable data correctly connected
      (verified through the full workflow: import → allocate → PDF)
- [x] No secrets exposed: H2 dev password is empty, docs show placeholders only,
      production credentials documented as environment variables

## 7. Documentation  `[x] Completed`

- [x] `README.md` added
- [x] `PROJECT_DETAILS.md` added — all 30 required sections, based on the actual
      code/structure (implemented vs planned clearly marked)
- [x] `database.md` updated for the new schema

## 8. Testing performed  `[x]`

- [x] Application build: `mvn clean package -DskipTests` → jar produced
- [x] Backend startup: Spring Boot starts cleanly (port 8081 for tests)
- [x] Database connection: H2 up, Hibernate DDL, H2 console enabled
- [x] Batch creation/retrieval: defaults A–G seeded; custom `H` add/duplicate/blank/delete-guard
- [x] Student creation/retrieval/update/delete incl. duplicate-roll rejection
- [x] Exam PDF upload / Excel upload / CSV upload (extraction + validation + idempotency)
- [x] Exam-data extraction: dates (`dd/MM/yyyy`, `yyyy-MM-dd`, invalid), times, application IDs
- [x] Timetable generation (allocation) for multiple exams — 14/14 students seated
- [x] Scheduling/conflict handling: clash endpoint, import conflict report, allocation warnings
- [x] PDF generation + final PDF data (header, roster and seat cells verified from extracted text)
- [x] Room grid defaults: Students/Desk defaults to 3; desk prediction stays wide
      & shallow (3–4 rows max: 12→3×4, 24→3×8, 20→4×5, 36→3×12)
- [x] Existing functionality: full CDP UI suite — ALL TESTS PASSED (39 checks, no page errors)

## Remaining / not part of the requirements  `[ ] Pending`

- [ ] Authentication & admin login (`Admin` entity is a Planned stub —
      **not required** by this requirement list, no auth was requested)
- [ ] JUnit unit-test classes in `src/test` (coverage currently provided by the
      end-to-end suite in `tools/test/`)

No requirement from this list is left unimplemented.
