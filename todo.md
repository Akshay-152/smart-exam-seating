# TODO — Implementation Status

Status legend: `[x]` implemented **and verified** · `[ ]` pending/not implemented

This file replaces the original requirement list with the actual implementation
status; each section below restates its requirement and what was built.

Last verified: build `room-allocation-system-0.0.1-SNAPSHOT.jar` — application
starts, `mvn test` green **on Java 25** (Mockito 5.24 / Byte Buddy 1.17.7 pinned
in `pom.xml`), `tools/test/cdp-test.mjs` **ALL UI TESTS PASSED** (40 checks,
including admin login, no page errors) and `tools/test/responsive-test.mjs`
**ALL RESPONSIVE TESTS PASSED** (rooms name/search/Select, allocation room
picker, zero page-level horizontal overflow at 1920/1366/1024/768/390/320px).

Update 2026-10-18 (full verification): a from-scratch build in an isolated copy
(`mvn clean verify`, project `target/` untouched) produced the jar with **20 unit
tests, 0 failures**, and the freshly built jar was run against an isolated H2
database (the running MySQL dev instance on :8080 was left untouched):
`exam-api-smoke.py` **20/20 PASS**, the repaired `duplicate-seat-test.py`
**PASS** (no duplicate seat/student allocations, cleaned up after itself),
`cdp-test.mjs` **ALL UI TESTS PASSED** (40 checks, no page errors),
`responsive-test.mjs` **ALL RESPONSIVE TESTS PASSED** (57 checks, no page errors)
and `responsible-test.mjs` **ALL RESPONSIBLE-MD CHECKS PASSED**. Test tooling also
fixed: `seed.sh` now signs in and sends CSRF (it was failing every POST with 403),
`duplicate-seat-test.py` no longer crashes on a `NameError`, and the two UI
suites accept `APP_URL` and enable CDP dialogs (confirm prompts previously hung).

Update 2026-10-18 (documentation sync): `PROJECT_DETAILS.md`, `README.md` and
`database.md` were re-checked against the source. The stale "authentication is not
implemented", "no `src/test` folder", single-`index.html` frontend and `Admin`
entity claims are gone: the docs now describe Spring Security with JSON login,
the admin/student portals (`index.html` + `app.js`), exam lifecycle and eligibility,
draft/published allocations, the full API surface, the 13 entities and the actual
test suites. Verified again with `mvn test` = **20 tests, 0 failures**.

Update 2026-10-18 (repair): the committed sources did not compile. Duplicate/incomplete
methods in `ExamController` (`addExam`, `updateExam`, `deleteExam`, plus throws of the
checked `javax.security.auth.login.AccountException`) and a duplicated
`unpublishAllocations` in `AllocationController` were removed; the redundant admin
re-check in `ExamController` is gone because `SecurityConfig` already restricts
`/api/exams/**` to `ROLE_ADMIN`. Two further defects were found and fixed while
covering the code with tests: `GET /api/exams?status=...` threw
`UnsupportedOperationException` (sorting an immutable `toList()`), and an exam whose
end time precedes its start time threw a `NullPointerException` from
`Exam.deriveDuration()` instead of a readable 400. Verified with a genuine from-scratch
build (`target/classes` removed first — the earlier `BUILD SUCCESS` came from Maven's
stale incremental compile): `mvn test` = **20 tests, 0 failures**
(`ExamControllerTest`, `ConflictServiceTest`, `RoomImportServiceTest`), and
`python tools/test/exam-api-smoke.py` = **ALL EXAM API CHECKS PASSED** (20 checks over
HTTP against MySQL: validation, draft/publish/cancel lifecycle, delete guard,
eligibility round-trip). See `things done.md` for the analysis.

Update 2026-10-15: the room import form moved from the **Rooms** tab to the
**Import Data → Rooms** tab (all file uploads now live on the Import Data
dashboard). Verified: 39-check UI suite passes, and a CSV uploaded through the
moved form reported `2 new rooms / 0 invalid` with both rooms listed on the
Rooms tab.

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

- [x] MySQL configured as the default database; local MySQL 8 available via
      `docker-compose.yml`, with connection settings documented in `database.md`
- [x] Entities/models verified: `Student`, `User`, `Batch`, `Exam`, `ExamEligibility`, `ExamRoom`, `Room`, `Seat`, `SeatAllocation`, `Announcement`, `AuditLog`, `Department`, `Subject`
- [x] Repositories verified (`findByRollNo`, `countByBatch`, `findByExamId`, …)
- [x] Services verified (allocation, imports, conflicts, batches, PDF)
- [x] Tables/relationships verified (student ↔ seat ↔ room ↔ exam; batch names
      referenced by students/exams — see `PROJECT_DETAILS.md` §27)
- [x] Student, batch/course, exam and timetable data correctly connected
      (verified through the full workflow: import → allocate → PDF)
- [x] Production credentials use environment-variable overrides; local MySQL
      development credentials are documented and must not be used in production

## 7. Documentation  `[x] Completed`

- [x] `README.md` added
- [x] `PROJECT_DETAILS.md` added — all 30 required sections, based on the actual
      code/structure (implemented vs planned clearly marked)
- [x] `database.md` updated for the new schema

## 8. Testing performed  `[x]`

- [x] Application build: `mvn clean package -DskipTests` → jar produced
- [x] Backend startup: Spring Boot starts cleanly (port 8081 for tests)
- [x] Database configuration: MySQL default with Hibernate DDL; H2 console is disabled
- [x] Batch creation/retrieval: defaults A–G seeded; custom `H` add/duplicate/blank/delete-guard
- [x] Student creation/retrieval/update/delete incl. duplicate-roll rejection
- [x] Exam PDF upload / Excel upload / CSV upload (extraction + validation + idempotency)
- [x] Exam-data extraction: dates (`dd/MM/yyyy`, `yyyy-MM-dd`, invalid), times, application IDs
- [x] Timetable generation (allocation) for multiple exams — 14/14 students seated
- [x] Scheduling/conflict handling: clash endpoint, import conflict report, allocation warnings
- [x] PDF generation + final PDF data (header, roster and seat cells verified from extracted text)
- [x] Room grid defaults: Students/Desk defaults to 3; desk prediction stays wide
      & shallow (3–4 rows max: 12→3×4, 24→3×8, 20→4×5, 36→3×12)
- [x] Existing functionality: full CDP UI suite — ALL TESTS PASSED (40 checks, no page errors)
- [x] Room file import (PDF/Excel/CSV) works from its new home, the
      **Import Data → Rooms** tab: upload → counts/preview shown → rooms appear
      on the Rooms tab (CSV fixture, `2 new / 0 invalid`)
- [x] Room management extras (to add.md): optional **Room Name** column/field,
      live **search** by number/name/building, availability **Select** checkbox
      persisted via `POST /api/rooms/{id}/status`, allocation **room picker**
      with total-capacity warning, and `roomIds` honored (inactive rooms
      filtered server-side) — all covered by `tools/test/responsive-test.mjs`
- [x] Authentication & admin/student login (role-based dashboards, BCrypt
      passwords, first-login password-change prompt, sessions, CSRF, rate limiting,
      audit log) — verified end to end in `things done.md` and both UI suites

## Remaining / not part of the requirements  `[ ] Pending`

- [x] JUnit unit test runs again on Java 25 — `mvn test` is green (Mockito and
      Byte Buddy upgraded via `mockito.version` / `byte-buddy.version` in
      `pom.xml`)

No requirement from this list is left unimplemented.
