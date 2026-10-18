add an folder dbpropertys and add the apropriate files,folder in it


and 


# Task: Improve Room Management Without Breaking Existing Features

Modify the existing Smart Exam Seating Management System built with Java, Maven, and MySQL.

First inspect the current project, database schema, room management page, seating allocation logic, and existing UI design. Make changes that fit the current application and its existing technology stack.

**Important:** Do not remove, replace, or break any existing features. Keep the current UI style, colors, layout, navigation, and component design. Make only the necessary improvements. Do not redesign the application or introduce unnecessary complexity.

## 1. Room Management Table

Update the existing Room Management tab to display a simple, clean table with these columns:

| Room Name | Room Number | Room Type | Select | Capacity | Actions |
|---|---|---|---|---:|---|
| Computer Lab | 06 | Laboratory | ☑ | 40 | Edit · Delete |
| Seminar Room | 07 | Seminar | ☑ | 60 | Edit · Delete |
| Lab 1 | 08 | Laboratory | ☐ | 30 | Edit · Delete |
| — | 09 | General Room | ☑ | 50 | Edit · Delete |

These are illustrative examples. Use the existing room fields and data wherever possible.

Requirements:
- **Room Name:** Optional. The administrator can enter names such as Lab 1, Lab 2, Seminar Room, Computer Lab, or Main Hall.
- **Room Number:** Required and unique according to the existing database rules.
- **Room Type:** Include this column only if the existing project supports room types or it can be added without unnecessary schema changes. Otherwise, retain the current room fields.
- **Select:** A small checkbox indicating whether the room is available for examination seating allocation.
- **Capacity:** Display the room's existing seating capacity.
- **Actions:** Provide Edit and Delete actions consistent with the existing interface.

If the room name is empty, display a dash or the existing room number as a fallback. Do not require a room name to save a room.

## 2. Add Room and Edit Room Forms

Modify the existing Add Room and Edit Room forms rather than creating a separate interface.

Allow administrators to enter or update:
- Room number.
- Optional room name.
- Existing capacity and seating configuration fields.
- Other room properties already supported by the project.

Keep the forms simple, compact, and consistent with the current UI. Use the existing buttons, inputs, dialogs, and validation styles.

Save changes to MySQL and ensure edited room details appear immediately in the room list.

## 3. Room Availability Checkbox

Add a checkbox to every room row under the Select column.

- Checked means the room is available for future seating allocations.
- Unchecked means the room must be excluded from new allocations.
- Save the checkbox state to MySQL.
- Preserve selections after page refreshes and application restarts.
- Show a small success or error notification when the state changes.
- Ensure searching or filtering does not reset selections on other rooms.

The checkbox must control actual allocation eligibility through the Java backend. It must not be a frontend-only feature.

If an unavailable room has existing published allocations, preserve them and warn the administrator before any operation that would affect those allocations.

## 4. Room Selection Before Seating Allocation

Integrate the room availability feature into the existing seating allocation workflow.

Before generating allocations:

1. Display the rooms marked available in Room Management.
2. Allow the administrator to select the rooms required for the current examination.
3. Show each selected room's number, name, and capacity.
4. Calculate the total selected seating capacity.
5. Warn if the capacity is insufficient for the number of students.
6. Generate the seating allocation using only the eligible rooms selected by the administrator.

Do not change the existing allocation algorithm unnecessarily. Preserve existing allocation rules and prevent duplicate seats, capacity violations, and conflicting allocations.

## 5. Search and Filtering

Add a small search field above the room table using the current search input style.

Allow searching by:
- Room number.
- Room name.

If supported by the existing project, also allow filtering by room availability and room type.

Keep search fast and simple. Show a clear message when no rooms match. Searching must not affect stored availability or delete room records.

## 6. Edit and Delete Actions

Retain the current Edit and Delete functionality.

- Edit should open the existing editing form with the selected room's data.
- Delete should display a confirmation dialog.
- Prevent accidental deletion of rooms that have existing allocations unless the application has an explicit, safe process for handling them.
- Display appropriate success and error notifications.
- Refresh the table after successful operations without requiring a full-page reload where the existing architecture supports it.

Do not remove other existing room actions or fields.

## 7. Responsive UI Improvements

Make the room management interface compatible with phones, tablets, laptops, and desktop screens while preserving the current design language.

- Keep the existing visual style and component patterns.
- On laptops, maintain the current table layout where possible.
- On phones, use a compact responsive table, controlled horizontal scrolling, or stacked room rows as appropriate.
- Keep checkboxes and action buttons easy to tap.
- Prevent content from overflowing the screen.
- Make the Add Room and Edit Room forms fit small screens.
- Ensure search fields and buttons adapt to available width.
- Preserve the existing navigation, sidebar, colors, typography, and spacing patterns.

Do not add complex animations, new design systems, oversized cards, unnecessary images, or heavy dependencies. The goal is a small, polished improvement, not a complete redesign.

## 8. Java, Maven, and MySQL Compatibility

Maintain compatibility with the project's existing Java version, Maven dependencies, backend architecture, and MySQL schema.

- Reuse existing entities, repositories, services, controllers, and API endpoints.
- Reuse existing room fields and database tables wherever possible.
- Add a database migration only if a new field is genuinely required.
- Preserve all existing room records and seating allocations.
- Validate availability and permissions on the backend.
- Ensure room creation, editing, deletion, searching, and selection work with the real database.

Do not replace the existing framework or introduce a second database.

## 9. Testing

Verify that:

1. Existing room records display correctly.
2. A room can be added with or without a name.
3. Room details can be edited.
4. Availability checkboxes persist in MySQL.
5. Unavailable rooms are excluded from new seating allocations.
6. Search works by room name and number.
7. Edit and Delete actions still work.
8. Existing features and allocations remain intact.
9. The interface works on phones and laptops.
10. The project builds and runs successfully using the existing Maven setup.

## Final Instruction

Make only the required modifications to the existing project. Follow the current UI style and preserve every existing feature.

Before editing, inspect the codebase and identify the smallest set of changes needed. After implementation, report the files modified, any database changes, and the tests performed.

**Expected result:** A simple, responsive Room Management tab with Room Name, Room Number, optional Room Type, Select checkbox, Capacity, and Edit/Delete actions, integrated with the existing Java + Maven + MySQL application without breaking anything.