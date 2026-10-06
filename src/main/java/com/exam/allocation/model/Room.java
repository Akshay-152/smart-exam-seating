package com.exam.allocation.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "room_type")
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomNumber;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int rowsCount;
    
    @Column(nullable = false)
    private int columnsCount;

    /** How many students share one desk/bench (exam desks often seat 2). */
    private int studentsPerDesk = 1;

    // @JsonIgnore breaks the Seat -> Room -> Seat serialization cycle that
    // previously made /api/rooms return an infinitely nested JSON payload.
    @JsonIgnore
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<Seat> seats;

    // Default constructor
    public Room() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getRowsCount() { return rowsCount; }
    public void setRowsCount(int rowsCount) { this.rowsCount = rowsCount; }

    public int getColumnsCount() { return columnsCount; }
    public void setColumnsCount(int columnsCount) { this.columnsCount = columnsCount; }

    /** Normalised accessor: a room always has at least one student per desk. */
    public int getStudentsPerDesk() { return studentsPerDesk < 1 ? 1 : studentsPerDesk; }
    public void setStudentsPerDesk(int studentsPerDesk) {
        this.studentsPerDesk = studentsPerDesk < 1 ? 1 : studentsPerDesk;
    }

    public List<Seat> getSeats() { return seats; }
    public void setSeats(List<Seat> seats) { this.seats = seats; }
}
