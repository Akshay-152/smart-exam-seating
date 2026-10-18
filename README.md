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
- **Room-list upload** — from the **Import Data → Rooms** tab, import room
  numbers and desk counts from PDF, Excel (.xlsx/.xls) or CSV; rows, columns and
  capacity are calculated automatically.
- **Seating allocation** — round-robin interleaving across branch·batch groups,
  subject-mixing at shared desks, re-runnable and idempotent.
- **Conflict detection** — same date + time + audience exam clashes, plus
  warnings when a student or room is double-booked by the generated chart.
- **PDF export** — landscape A4 seating chart (colour-coded by branch·batch)
  with a full **Student List** table: Roll No | Name | Branch | Batch/Course |
  Room | Seat.
- **Single-page admin UI** — Dashboard, Students, Rooms, Courses, Exams,
  Seating Chart and Import tabs.
- **Authentication & roles** — server-side login for the administrator and for
  every student (roll number as initial password, password change required on
  first login, BCrypt hashes), session cookies, CSRF protection, login rate
  limiting and an audit trail of admin actions. Students can only read their
  own records; every admin API rejects student sessions.
- **Room management** — optional room **name** (dash shown when empty),
  building/floor, live **search** by number/name/building, an availability
  **Select** checkbox persisted in MySQL and enforced by the allocator
  (inactive rooms are never used for new charts), plus a **room picker** on the
  Seating Chart tab that shows total selected capacity and warns when it is
  short of the eligible-student count.
- **Responsive, mobile-first UI** — fluid add/edit form grid (6 → 3 → 2 → 1
  columns), tab strip that scrolls inside its own container, tables wrapped in
  dedicated scroll areas and touch-friendly controls: verified with **no
  page-level horizontal overflow at 1920/1366/1024/768/390/320 px**.

## Technology

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 17+ (tested on Java 25), Spring Boot 3.2 (Web, Data JPA, Security) |
| Database  | MySQL 8 by default (Docker Compose); MySQL/PostgreSQL drivers included |
| PDF       | Apache PDFBox 2.0.31 (generation **and** text extraction for import) |
| Excel     | Apache POI 5.2.5 |
| Frontend  | Single-page HTML/CSS/vanilla JS served from `src/main/resources/static/index.html` |
| Build     | Maven (`./.mvn-tool/apache-maven-3.9.9/bin/mvn` bundled) |

## Run it

```bash
#1. Build
./.mvn-tool/apache-maven-3.9.9/bin/mvn clean package -DskipTests

#2. Start MySQL
docker compose up -d

#3. (Optional) configure via environment variables
#    copy .env.example to .env and edit; without it the docker-compose defaults are used
cp .env.example .env

#4. Start the app (port 8080 by default; DB defaults match docker-compose.yml)
java -jar target/room-allocation-system-0.0.1-SNAPSHOT.jar

#5. Open the UI and sign in
#    http://localhost:8080
#    Administrator: admin / admin      (development-only default)
#    Student:       roll number as both username and password
```

Credentials, database name and the admin seed account are all configurable
through the environment variables listed in `.env.example` (`DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`).
Serve the app over HTTPS in production and change the demo admin password
before deployment.

Connect MySQL Workbench to `127.0.0.1:3306` with username `root` and local
development password `Akshay@6235`; refresh Schemas to browse `room_allocation_db`
(see [mysql-commands.md](mysql-commands.md) for SQL commands to show the data).
The app accepts `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables
to override its MySQL connection settings. See `database.md` for details.

## Typical workflow

1. **Students** tab — add students (Roll No, Name, Branch, Batch) or import a list.
2. **Rooms** tab — add rooms manually.
3. **Courses** tab — keep the defaults A–G or add custom batches.
4. **Import Data** tab — upload students, rooms or the exam schedule as PDF/Excel/CSV (or create exams manually).
5. **Exams** tab — review exams and any reported scheduling conflicts.
6. **Seating Chart** tab — generate the chart, check conflict warnings, download the PDF.

## Tests

```bash
# Unit tests (JUnit + Mockito — runs on Java 25; Byte Buddy / Mockito are
# pinned in pom.xml to versions that support class-file version 69)
./.mvn-tool/apache-maven-3.9.9/bin/mvn test

# Seed baseline data (app must be running on :8081)
bash tools/test/seed.sh http://localhost:8081/api

# End-to-end UI suite through Chrome DevTools Protocol (signs in as admin first)
# (start Chrome first: chrome.exe --headless=new --remote-debugging-port=9333 --user-data-dir=%TEMP%/chrome-test http://localhost:8081/)
node tools/test/cdp-test.mjs

# Rooms + responsiveness suite (app on :8080): room name/search/Select,
# allocation room picker, and horizontal-overflow sweep at 1920→320px
node tools/test/responsive-test.mjs
```

Fixture generators live next to the tests: `make_xlsx.py`, `make_exam_xlsx.py`,
`MakePdf.java`, `MakeExamPdf.java`.

## Documentation

- `PROJECT_DETAILS.md` — full architecture, database, API, scheduling, PDF and
  developer guide (30 sections).
- `database.md` — database configuration and entity/table mapping.
- `plan.md` — original project plan.
- `todo.md` — implementation status of the requirements.
