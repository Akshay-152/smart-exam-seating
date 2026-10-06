# Database Management

This document explains how the database is connected and managed in the Room Allocation System.

## Overview
The project uses **Spring Data JPA** (Java Persistence API) paired with **Hibernate** as the Object-Relational Mapping (ORM) tool. This allows us to interact with the database using Java objects (Entities) without writing raw SQL queries.

## Current Configuration (H2 In-Memory)
To make testing seamless, the system is currently configured to use an **H2 In-Memory Database**. This means the database is created in RAM when the application starts and is destroyed when it stops. 

The connection properties are defined in `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:room_allocation_db
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.h2.console.enabled=true
```
* **H2 Console:** You can view the live database and run queries by navigating to `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:room_allocation_db`, Username: `sa`, Password: `[blank]`).

## Switching to MySQL (Production Ready)
When you are ready to switch to a persistent database like MySQL, you simply update `application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/room_allocation_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```
* Note: A `docker-compose.yml` file is also included in the root directory if you wish to easily spin up a MySQL instance.

## How Entities Map to Tables
The system uses the `@Entity` annotation on Java classes to automatically generate database tables.
* `Student`: Maps to the `student` table.
* `Room`: Maps to the `room` table.
* `Seat`: Maps to the `seat` table (contains Foreign Keys to `Room` and `Student`).
* `Exam`: Maps to the `exam` table.
* `Admin`: Maps to the `admin` table.

Because we set `spring.jpa.hibernate.ddl-auto=update` in the properties, Hibernate will automatically create or update the table schemas every time the application starts based on these Entity classes.

## Repositories
Data access is handled by interfaces extending `JpaRepository` (e.g., `StudentRepository`). Spring automatically generates the implementation at runtime, providing instant access to methods like `findAll()`, `save()`, and `findById()`.
