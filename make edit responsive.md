# Task: Fix Room Management Responsiveness Without Changing the Existing UI Design

Analyze the existing Smart Exam Seating Management System and fix the responsiveness issues visible in the Room Management page, especially when adding or editing rooms.

The application currently uses Java, Maven, and MySQL. Preserve the existing architecture, database, room management functionality, seating allocation logic, and UI design.

## 1. Main Problem

The Room Management page overflows horizontally when the room table and editing inputs are displayed. The page becomes wider than the browser viewport, and a horizontal scrollbar appears across the entire page.

Fix the root cause. Do not simply hide the scrollbar or shrink all content.

## 2. Responsive Main Container

- Use a fluid container with `width: 100%` and an appropriate `max-width`.
- Use `box-sizing: border-box` consistently.
- Remove unnecessary fixed widths and large minimum widths.
- Ensure the main page, cards, forms, and navigation do not expand beyond the viewport.
- Prevent page-level horizontal overflow at common desktop, tablet, and mobile widths.
- Preserve the existing background, white content panels, border radius, shadows, typography, colors, and active navigation styles.

## 3. Responsive Add/Edit Room Form

Keep the existing fields and features, including Room Number, Room Name if supported, Building, Floor, Desks, Students per Desk, Rows, Columns, Capacity, and the Add Room or Save Changes button.

Use CSS Grid or Flexbox to adapt the form:

- Wide desktop: multiple columns, using the available space.
- Small laptop or tablet: two or three columns.
- Mobile: one or two columns depending on available width.
- Very narrow screens: one column.

Make all inputs use `width: 100%` and `min-width: 0`.

Keep labels above their inputs. Ensure the Add Room and Save Changes buttons fit naturally and do not overlap fields.

Do not remove any existing form field, automatic row/column calculation, capacity calculation, validation, or editing behavior.

## 4. Responsive Room Table

Keep the existing room table and its current data.

Display existing columns such as:

- Room Number
- Building
- Floor
- Rows
- Columns
- Desks
- Students per Desk
- Capacity
- Status
- Actions

If Room Name and room availability selection already exist or are being implemented, retain them as well.

On desktop, show the complete table where practical.

On smaller screens, place the table inside a dedicated responsive wrapper using `overflow-x: auto` and `max-width: 100%`. The horizontal scrollbar must remain inside this wrapper, not at the page level.

For phones, use compact room cards instead if this fits the existing UI architecture better. Each card must preserve the room details, availability checkbox, Edit action, and Delete or Deactivate action.

Do not remove existing columns or actions just to make the table fit.

## 5. Fix Edit Mode

When clicking Edit:

- Keep the editing form inside the normal page container.
- Do not let editable table inputs increase the page width.
- Use responsive input widths and appropriate minimum widths.
- If inline editing makes the table too wide, use the existing form layout or a responsive edit dialog instead.
- On mobile, the dialog must fit the viewport and allow vertical scrolling when needed.
- Preserve the existing edit, save, and cancel behavior.

Avoid having both the table and editing inputs force the entire document to become wider.

## 6. Navigation Responsiveness

Preserve the existing Dashboard, Students, Rooms, Courses, Exams, Seating Chart, and Import Data navigation.

On desktop, keep the current horizontal navigation.

On smaller screens, allow the navigation items to scroll horizontally within their own container or use an appropriate compact menu. Do not let the navigation increase the document width.

Preserve the existing active-tab styling.

## 7. Search and Room Actions

If room search and availability selection are already implemented, keep them working.

Ensure search inputs, selection checkboxes, and action buttons fit the available space. Allow action buttons to wrap or use a compact layout on mobile.

Do not reset checkbox selections when searching or editing a room.

## 8. Technical Requirements

- Inspect the existing HTML, CSS, JavaScript, and Java backend before editing.
- Reuse the current CSS framework and components.
- Avoid adding unnecessary libraries.
- Keep all existing API routes, database operations, validation, and seating allocation logic intact.
- Do not make changes to MySQL schema unless required by the room management features.
- Fix CSS and layout issues at their source.
- Do not use `overflow-x: hidden` on the entire page as a workaround.
- Avoid fixed heights that cut off forms or table content.

## 9. Testing Requirements

Test the actual Room Management page and Edit workflow at these viewport widths:

- 1920px
- 1366px
- 1024px
- 768px
- 390px
- 320px

Verify that:

1. The page does not overflow horizontally.
2. The room form fields fit the available width.
3. Editing a room does not stretch the entire page.
4. The table scrolls only within its wrapper when necessary.
5. Navigation remains usable.
6. Add, Edit, Delete, Deactivate, search, and selection features still work.
7. The room table displays the correct data from MySQL.
8. The application builds and runs using the existing Maven setup.

## Final Instruction

Make the smallest set of changes needed to fix the responsive layout. Preserve the existing visual style and all working functionality. Do not redesign the application or replace the current UI with a different template.

After implementation, summarize the modified files, explain the root cause of the overflow, and report which viewport sizes and workflows were actually tested.