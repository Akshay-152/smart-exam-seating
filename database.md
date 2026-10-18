# Database Management

This document explains how the database is connected and managed in the Room Allocation System.

## Overview
The project uses **Spring Data JPA** (Java Persistence API) paired with **Hibernate** as the Object-Relational Mapping (ORM) tool. This allows us to interact with the database using Java objects (Entities) without writing raw SQL queries.

## Current Configuration (MySQL)
The application uses MySQL by default. Start the local MySQL 8 server with Docker Compose:

```powershell
docker compose up -d
```

The default connection settings in `src/main/resources/application.properties`
match the Compose service (user `root`, password `Akshay@6235`) — the same
password is used for a natively installed MySQL 8 on port 3306:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/room_allocation_db?createDatabaseIfNotExist=true
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:Akshay@6235}
```

Run the Spring Boot app after MySQL has started. Hibernate creates or updates
the tables at startup.

### Connect with MySQL Workbench
Create a connection using:

| Setting | Value |
|---|---|
| Hostname | `127.0.0.1` |
| Port | `3306` |
| Username | `root` |
| Password | `Akshay@6235` (local development default) |

After connecting, refresh **Schemas** and open `room_allocation_db` to browse
its tables. For the SQL commands that show data, see
[`mysql-commands.md`](mysql-commands.md). The Compose root password can be overridden with the
`MYSQL_ROOT_PASSWORD` environment variable; set the same password for the Spring
app using `DB_PASSWORD`.

For a non-Docker MySQL installation, create the `room_allocation_db` schema and
set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables before
launching the app. Do not use the local development password in a shared or
production environment.

H2 remains included as a runtime dependency for explicitly configured tests or
demo use, but the H2 web console is not enabled by default.

## How Entities Map to Tables
The system uses the `@Entity` annotation on Java classes to automatically generate database tables.
* `Student`: Maps to the `student` table (`roll_no`, `name`, `branch`, `batch` - exactly the four required student fields; there is no Division column).
* `Batch`: Maps to the `batch` table (`name` unique, `custom` flag) - holds the default batches A-G plus any custom "Add Course" entries.
* `Room`: Maps to the `room` table.
* `Seat`: Maps to the `seat` table (contains Foreign Keys to `Room`, `Student` and `Exam`).
* `Exam`: Maps to the `exam` table (`subject_name`, `branch`, `course` (batch/course target), `exam_date`, `exam_time`, `application_id`).
* `Admin`: Maps to the `admin` table (Planned - authentication is not implemented yet).

Because we set `spring.jpa.hibernate.ddl-auto=update` in the properties, Hibernate will automatically create or update the table schemas every time the application starts based on these Entity classes.

## Repositories
Data access is handled by interfaces extending `JpaRepository` (e.g., `StudentRepository`, `BatchRepository`, `ExamRepository`). Spring automatically generates the implementation at runtime, providing instant access to methods like `findAll()`, `save()`, `findById()` plus derived queries such as `findByRollNo`, `findByBatch`, `countByBatch`, `findByExamId` and `findByNameIgnoreCase`.

See `PROJECT_DETAILS.md` sections 7, 8 and 27 for connection details, example SQL and the ER diagram.
