# Room Allocation System - Project Plan

## 1. Introduction
The **Exam Room and Seat Allocation Management System** is a computerized solution to automate the process of assigning exam rooms and seats to students. It replaces the slow, error-prone manual seating work with a centralized, automatic system that ensures fair, conflict-free, and malpractice-resistant seating arrangements.

## 2. Requirements

### Hardware Requirements
- **Processor:** Intel Core i3 or above
- **RAM:** 4 GB minimum (8 GB recommended)
- **Storage:** 500 MB free disk space
- **Display/Input:** Standard display, keyboard, and mouse
- **Printer:** (Optional) For printing seating charts

### Software Requirements
- **Backend Language:** Java (Object-Oriented Programming)
- **Web Framework:** Spring Boot (recommended for Java web output) or Servlets/JSP. 
- **Frontend:** HTML, CSS, Vanilla JavaScript (to provide the requested web output)
- **Database:** MySQL / PostgreSQL
- **Connectivity:** JDBC or Spring Data JPA
- **IDE:** Eclipse / IntelliJ IDEA / NetBeans
- **Operating System:** Windows / Linux (any OS supporting Java)

## 3. System Architecture & Design
To fulfill the requirement of a **Web Output** while using **Java as the main language**, we will adopt a modern Client-Server web architecture:

**User (Admin) -> Web Browser (Frontend) -> HTTP/REST -> Java Web Server (Backend) -> JDBC/JPA -> Database**

1. **Frontend (Web Output):** Built using HTML, CSS, and JavaScript. It provides an Admin interface to input exam details, manage rooms/students, and view/print generated seating plans.
2. **Backend (Java Application):** Acts as the web server handling HTTP requests from the frontend. It contains the core OOP classes (`Student`, `Room`, `Seat`, `Exam`, `Admin`) and the complex allocation logic. It validates capacity, applies constraints (class mixing, roll-number sequencing), and generates the seating plan.
3. **Database (MySQL/PostgreSQL):** Stores master data (students, rooms, seats, exams) and allocation results persistently.

## 4. Object-Oriented Modeling (Core Classes)
The system models real-world entities to build a clean, modular, and extensible solution:
- `Admin`: Manages the system, inputs data, and triggers allocation.
- `Student`: Represents a student with attributes like Roll No, Name, Course/Class.
- `Room`: Represents an exam room with capacity, layout, and room type (e.g., regular vs hall - using inheritance).
- `Seat`: Represents a specific location in a room.
- `Exam`: Represents an examination event with date, time, and assigned courses.
- `AllocationManager`: The core service containing the algorithm to distribute students across rooms and seats fairly, avoiding clashes.

## 5. Implementation Phases
* **Phase 1: Database Setup** - Design and create tables for Students, Rooms, Exams, and Allocations.
* **Phase 2: Backend Core Logic** - Implement the Java OOP models and the automatic seat allocation algorithm ensuring all constraints (no adjacent students from same course, capacity validation).
* **Phase 3: Web API Development** - Expose the Java logic via RESTful endpoints (using Spring Boot or Servlets).
* **Phase 4: Frontend Development** - Build a rich, dynamic web interface (HTML/CSS/JS) for the Admin panel.
* **Phase 5: Integration & Testing** - Connect the web frontend to the Java backend and thoroughly test the seating chart generation and reports.
