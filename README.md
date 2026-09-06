# VoidReach CRM

VoidReach is a local-first desktop CRM workspace built with Java 26 and JavaFX 26. It brings account management, contacts, calendar planning, task control, dashboards, and linked TXT/Markdown notes into one native application.

Workspace data is persisted locally and separated by account, with automatic recovery and backup support. Local storage is the current source of truth, allowing the application to operate independently while keeping each user's data available on the device.

The project is designed to support a future online service that can make workspace data available across devices and provide a controlled migration path from local files to cloud-backed storage. This cloud capability is part of the planned roadmap and is not yet enabled in the current local-first build.

> **Repository notice:** This public repository is provided solely for source-code inspection and to showcase the author's work on GitHub. Public access does not grant permission to use, copy, modify, redistribute, or incorporate the code, in whole or in part, into a fork, personal or third-party project, product, or service. Commercial, professional, production, and business use of the application requires the purchase of an authorized commercial version or a separate written license from the author.

## Contents

- [Current Status](#current-status)
- [Screenshots](#screenshots)
- [Feature Overview](#feature-overview)
  - [Accounts and Authentication](#accounts-and-authentication)
  - [Workspace Search and Quick Actions](#workspace-search-and-quick-actions)
  - [Home](#home)
  - [Dashboard](#dashboard)
  - [Contacts](#contacts)
  - [Calendar](#calendar)
  - [Tasks](#tasks)
  - [Notes](#notes)
  - [Agenda Sidebar](#agenda-sidebar)
  - [Themes](#themes)
  - [Profile Picture](#profile-picture)
- [Keyboard and Mouse Reference](#keyboard-and-mouse-reference)
- [Local Persistence and Recovery](#local-persistence-and-recovery)
- [Requirements](#requirements)
- [Running the Application](#running-the-application)
- [Tests](#tests)
- [Native Packaging](#native-packaging)
- [Technology](#technology)
- [Project Structure](#project-structure)
- [Repository Purpose and Usage Restrictions](#repository-purpose-and-usage-restrictions)
- [License](#license)

## Current Status

The following sections are operational:

- Home
- Dashboard
- Contacts
- Calendar
- Tasks
- Notes
- Account and profile management
- Per-account themes
- Workspace search and quick actions
- Settings, shortcut help, and task reminders
- Local persistence and automatic backups

Settings includes account controls, appearance, import/export, checkpoint restoration, and optional local encryption/recovery keys. The Help control opens a shortcut guide, and the bell opens overdue tasks (or today's tasks when nothing is overdue). Reminders are shown inside the app; they are not operating-system notifications.

## Screenshots

The following samples use the same professional demo workspace across all four available themes.

### Calendar and focused notes

![Calendar month view at compact width](sample/screenshots/calendar-month.png)

![Overlapping appointments in Day view](sample/screenshots/calendar-day.png)

![Focused note editing](sample/screenshots/notes-focus.png)

### Workspace search

![Workspace search and quick actions](sample/screenshots/workspace-search.png)

### Blue-gray

![Blue-gray Home](sample/screenshots/blue-gray/home.png)

![Blue-gray Dashboard](sample/screenshots/blue-gray/dashboard.png)

![Blue-gray Contacts](sample/screenshots/blue-gray/contacts.png)

![Blue-gray Calendar](sample/screenshots/blue-gray/calendar.png)

![Blue-gray Tasks](sample/screenshots/blue-gray/tasks.png)

![Blue-gray Notes](sample/screenshots/blue-gray/notes.png)

### Gray Blue

![Gray Blue Home](sample/screenshots/gray-blue/home.png)

![Gray Blue Dashboard](sample/screenshots/gray-blue/dashboard.png)

![Gray Blue Contacts](sample/screenshots/gray-blue/contacts.png)

![Gray Blue Calendar](sample/screenshots/gray-blue/calendar.png)

![Gray Blue Tasks](sample/screenshots/gray-blue/tasks.png)

![Gray Blue Notes](sample/screenshots/gray-blue/notes.png)

### Dark

![Dark Home](sample/screenshots/dark/home.png)

![Dark Dashboard](sample/screenshots/dark/dashboard.png)

![Dark Contacts](sample/screenshots/dark/contacts.png)

![Dark Calendar](sample/screenshots/dark/calendar.png)

![Dark Tasks](sample/screenshots/dark/tasks.png)

![Dark Notes](sample/screenshots/dark/notes.png)

### Light

![Light Home](sample/screenshots/light/home.png)

![Light Dashboard](sample/screenshots/light/dashboard.png)

![Light Contacts](sample/screenshots/light/contacts.png)

![Light Calendar](sample/screenshots/light/calendar.png)

![Light Tasks](sample/screenshots/light/tasks.png)

![Light Notes](sample/screenshots/light/notes.png)

## Feature Overview

### Accounts and Authentication

- Local accounts with independent workspaces, profiles, avatars and theme preferences.
- Passwords of at least eight characters; new hashes use salted PBKDF2-HMAC-SHA256 with 600,000 iterations. Existing 120,000-iteration hashes remain readable and upgrade after successful password sign-in.
- Remembered-email sign-in is a convenience for **unprotected profiles only**. Encrypted profiles always require a password.
- **Settings → Encryption & recovery key** offers optional AES-256-GCM encryption of workspace records, trash, managed backups and prior workspace revisions. Encryption is off until explicitly enabled.
- Setup displays a random offline recovery key and requires confirmation that you stored it safely. Password and recovery key independently wrap the workspace's random data key.
- Password recovery requires that previously stored offline key. No code is generated on the sign-in screen and no email is sent. Keys are single-use; create a replacement from Settings after recovery.
- Changing a password rewraps the data key; it does not discard encrypted workspace data.
- Account names/emails, avatars and exported workspace/ICS files remain unencrypted. Protect the operating-system account and exported files too.

Without a password or valid recovery key, encrypted data cannot be recovered. Keep the account metadata (`users.properties`, containing wrapped keys) together with encrypted workspace backups when backing up or moving the complete local profile. An encrypted raw backup is read from its originating, unlocked profile; use the explicitly unencrypted portable export for transfer to another profile.

The cryptographic choices follow [OWASP password-storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) and [authenticated-encryption guidance](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html). They are not a substitute for an independent security audit.

### Workspace Search and Quick Actions

- Open the top-bar search or press `Ctrl+K` / `Cmd+K` from any section.
- Search contacts, including custom fields; tasks, including descriptions; and notes, including their content.
- Search ignores case and accents, matches every entered word, and ranks title matches first.
- Open a result directly in its existing editor. The index is rebuilt from the active account each time search opens.
- With no query, choose quick actions to create a contact, task, or note; review due work; or navigate between sections.
- Use the arrow keys to select a result, `Enter` to open it, and `Esc` to close search.
- Press `Ctrl+Shift+N` / `Cmd+Shift+N` to create a task, or `F1` for help.
- The top bar adapts to the available width; sidebars collapse automatically to preserve readable workspace content and return when space permits.

### Home

The Home section provides a daily operational summary:

- Personalized greeting and current date.
- Total number of contacts.
- Number of open tasks scheduled today.
- Number of open tasks scheduled during the inclusive seven-day window (today through day six).
- Next open task and its time, including ongoing activities and tasks on later dates.
- Today's agenda.
- Upcoming tasks.
- Contacts worth reconnecting with, based on dated interaction history (unknown legacy dates are shown as unknown).
- Quick actions for creating a contact or task.
- An overdue-work summary that opens the matching task filter.
- Clickable task and contact rows, also accessible with `Tab` and `Enter` / `Space`.

Completed tasks are excluded from the next-task and upcoming summaries. Time-dependent summaries and reminders refresh every 30 seconds while the main window is open.

### Dashboard

The Dashboard provides actionable summaries generated from the active workspace:

- Total contacts.
- Total calendar tasks.
- Upcoming tasks during the next seven days.
- Planned hours for the next seven days, excluding completed and all-day events.
- An attention panel for overdue/high-priority/unplanned work and contacts to reconnect with.
- Clickable overloaded days, based on the configured daily working-hours capacity.
- Contact distribution by tag.
- Upcoming workload chart grouped by day.
- Recorded contact interactions.

Dashboard values update whenever contacts or tasks change.

### Contacts

- Create, view, edit, and delete contacts.
- Open a contact profile by clicking its table row: dated interaction timeline, linked tasks/appointments and linked notes.
- Log calls, emails and meetings, create a linked follow-up, or start a meeting note directly from the profile.
- Contact forms retain invalid drafts and require a name; email is optional but validated when supplied.
- Store name, company, job title, email, phone, last interaction, tag, and description.
- Tags: `Empty` (no tag, the default for new contacts), `Client`, `Tech`, and `Follow-up`.
- The description is shown in its own table column as a single-line preview with ellipsis; hovering shows the full text in a wrapping tooltip.
- Search in real time by contact information and description.
- Sort any visible field, including description and custom fields, in ascending or descending order.
- Clear the active sorting rule.
- Paginate results using 15, 25, 50, or 100 rows, or display all contacts.
- Enable selection mode for single or bulk deletion.
- Copy selected contacts to the clipboard with `Ctrl+C` or `Cmd+C`.
- Resize and reorder table columns with the mouse.
- Rename a column by clicking directly on its label; dragging the surrounding header continues to reorder the column.

#### Quick edit

- The **Quick edit** toggle in the toolbar switches the table to in-place editing: click a cell and type, press `Enter` or click away to save, `Esc` to cancel.
- The tag cell edits through a drop-down list and the description cell through a multi-line editor (`Ctrl+Enter`/`Cmd+Enter` saves).
- While Quick edit is on, **New contact** inserts an empty row directly in the table and starts editing its name; the full pop-up editor stays available with a right click on the row.
- The Quick edit state is saved per account and restored at the next sign-in.

#### Custom fields

- **Add field** in the toolbar, or **＋ Add field** inside the contact pop-up, adds a user-defined column (for example `Automobile`) to the table and to the pop-up editor.
- Field names must be unique; custom fields support inline editing, sorting, and renaming from the column header.
- Right-click a custom column header to remove the field; its stored values are deleted after confirmation.
- Custom field definitions and values are persisted per account.

### Calendar

- **Day, Week, Month and Agenda** views; the timed views cover the full 24 hours.
- Overlapping events use separate columns. Day headers and all-day events stay fixed while the timeline scrolls.
- Quiet fifteen-minute grid, highlighted working hours, a current-time line, and a clear **+ New** action.
- Click an empty time slot to create an appointment, or drag to select a time range.
- Click an event for quick details, linked notes, Edit, Duplicate and Delete; keyboard users can use Tab and Enter/Space.
- Drag to reschedule, including between weekdays; resize from the lower edge. The **Snap** menu in the Day/Week toolbar offers **Grid (15 min), 1, 5, 10, 15 or 30 minutes**; the same choice is available in Calendar preferences and persists per account. Grid follows the visible quarter-hour lines. Snapping aligns the resulting start/end time even for appointments originally off-grid. Alt temporarily uses 1 minute and Esc cancels without changing data. The timeline scrolls near the upper/lower viewport edges during a drag.
- Validation happens before the editor closes. Invalid dates/times retain the draft and show a pinned error.
- All-day and multi-day events; positive whole-minute timed durations up to 366 days.
- Daily, weekly and monthly recurrence, interval, optional count or end date, and exclusions. Monthly repeats skip invalid month dates. Changes/deletions apply to one occurrence, this and following occurrences, or the entire series.
- Optional reminders while the app is open, with persisted ten-minute snooze and dismissal. There is no background reminder delivery when the app is closed.
- First weekday, working hours, snapping, selected date, view and zoom persist per account.
- Zoom from 0.75× to 3×, Ctrl/Cmd + wheel to zoom and Ctrl/Cmd+0 to reset.
- Month offers direct day navigation and overflow links; Agenda virtualizes the next 90 days. Extremely dense days (over 200 timed events) link to the full Agenda instead of constructing thousands of timeline nodes.
- **Calendar → ⋯** includes preferences and ICS import/export.

ICS follows the supported subset of [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545): UTF-8 folding, stable UIDs, local/UTC/IANA-zone one-off events, all-day spans, relative start reminders, DAILY/WEEKLY/MONTHLY rules, COUNT/UNTIL and EXDATE. Unsupported recurrence constructs are reported; a series with RECURRENCE-ID overrides is skipped as a whole instead of importing an incorrect master. Recurring foreign-time-zone series are not silently converted. Exports use floating local times and are **not encrypted**; unscheduled tasks are excluded.

Linked notes are available in event details, the agenda and the editor's searchable multi-select list, with folder labels. Calendar, Tasks and contact profiles share the same records.

### Tasks

- Tasks can be **unscheduled**, with an optional day-based deadline, or scheduled as appointments.
- Priorities: Low, Normal, High and Urgent. States: To do, In progress and Completed.
- Search by title/description; filter All tasks, Today, Upcoming, Overdue, Completed, Unscheduled, High priority and In progress.
- No-deadline tasks are not treated as overdue and do not invent a midnight appointment.
- Grouped dates, readable status and priority labels, linked notes, editing, completion and calendar navigation.
- Lists render 100 rows per page. All non-repeating records are included; recurring occurrences use an explicit 30-day past / 366-day future horizon.
- Changes immediately update Calendar, Home and Dashboard. Deleted records can be restored from History.

Calendar and Tasks operate on the same data; appointments and unscheduled work remain distinct.

### Notes

The Notes section provides a per-account knowledge workspace.

- Blank, Meeting, Call and Project brief templates.
- Direct contact linking as well as task links.
- Focus mode hides sidebars and metadata; appearance controls are collapsed by default.
- Saving feedback reflects pending edits and actual disk completion. Typing no longer rebuilds a hidden note library.

#### Note Library

- Create any number of notes.
- Create persistent folders with **Add folder** and open them like a dedicated workspace.
- Create nested folders by using **Add folder** while another folder is open.
- Navigate the complete clickable breadcrumb path, such as **Home › Projects › Client A**.
- Drop a note onto any breadcrumb segment to move it directly to that location.
- The breadcrumb segment currently under the pointer is highlighted during a drag, making the destination name clear before dropping.
- The breadcrumb bar scrolls long paths and automatically keeps the current location visible.
- Dropping on **Home** moves the note to **All notes**; dropping on a folder name moves it into that folder.
- Drag a complete note card onto a folder to move it there.
- Drag a folder card onto another folder to move the complete folder tree, or drop it on a breadcrumb to move it to an ancestor location.
- Invalid folder moves that would create a cycle, target the same location, or duplicate a sibling folder name are rejected.
- Move a note between folders, or back to **All notes**, from the editor's folder selector, which displays each folder's complete breadcrumb path.
- Rename folders or delete them; deleting a folder safely moves its notes and subfolders to the deleted folder's parent location.
- Choose Plain text (`.txt`) or Markdown (`.md`) when creating a note.
- Search by title or content.
- Reorder note cards with drag and drop.
- The chosen order is persisted.
- Click a card to open a full-workspace editor.
- Rename notes from the title field.
- Delete notes with confirmation.
- Changes are saved automatically.

TXT and Markdown are stored as note formats inside the local VoidReach workspace. They are not currently exported as standalone filesystem files.

#### Typography

Both TXT and Markdown notes support per-note presentation settings:

- Any font family installed on the system.
- Font sizes from 12 to 48 px.
- Regular, Medium, Semibold, and Bold weights.
- Quick whole-editor Bold control.
- Whole-editor italic style.
- Reset to the default typography.

The settings are saved with the note and restored the next time it is opened.

Inter is bundled for the interface, plain-text editor and reading view; JetBrains Mono is bundled for Markdown source and code. The “Default” choice uses these fonts without requiring an OS installation. Editor appearance and reading appearance are independent; existing custom font choices are preserved.

All four themes share one application type system in css/typography.css: page titles at 28 px, panel titles at 15 px, interface text at 13 px, secondary text at 12 px and compact captions at 11 px. Regular, Medium and Semibold are actual bundled faces, not OS-dependent approximations.

#### Markdown Editing

Markdown notes use a RichTextFX `CodeArea`, so the text, syntax colors, selection, and blinking insertion cursor are rendered by one native editor control.

- Heading, bold, italic, link, code, and checklist toolbar actions.
- `Ctrl+B`/`Cmd+B` wraps the selection in Markdown bold syntax.
- `Ctrl+I`/`Cmd+I` wraps the selection in Markdown italic syntax.
- Tab inserts four spaces for code indentation.
- Inline code uses single backticks.
- Selecting multiple lines and pressing Code creates a fenced code block with triple backticks.
- The editor still highlights older multiline single-backtick spans; Preview follows CommonMark, where these are inline spans. Use triple-backtick fences when line breaks and indentation must be preserved.
- Obsidian-style wiki links such as `[[Project plan]]` open the matching note from Preview.
- Standard absolute HTTP/HTTPS links open in the system browser; mail links use the system mail app. Unsupported schemes are blocked. Missing or ambiguous wiki links show a message.

#### Live Code Highlighting

Code can be written directly in the note editor and displayed with IDE-style semantic syntax highlighting while you type, making keywords, types, variables, functions, literals, comments, and operators visually distinct.

Code inside backticks or fenced blocks is highlighted while editing. Preview highlights fenced blocks with a language label; unlabelled blocks and inline code remain neutral and readable. The lightweight multilingual lexer distinguishes common:

- Keywords
- Data types and class names
- Variables
- Functions
- Strings
- Numbers
- Comments
- Annotations
- Operators

The highlighter is language-agnostic and designed for common Java, JavaScript, Python, SQL, and similar syntax. It does not replace a compiler or a full language server.

#### Markdown Preview

- Separate, selectable reading surface powered by CommonMark with table, strikethrough, task-list and heading-anchor extensions.
- All six heading levels, proper paragraphs, ordered/nested lists, read-only checklists, blockquotes, separators, emphasis, tables, inline code, fenced and indented blocks.
- Syntax-highlighted fenced code with exact whitespace and a Copy button; long code and tables scroll horizontally.
- Copy selected text with Ctrl+C/Cmd+C, or use Copy text for the whole rendered note.
- Clickable Obsidian-style note links.
- Independent Preview font family.
- Independent Preview font size from 12 to 48 px.
- Custom Preview text color.
- Reset reading appearance to the active-theme defaults.
- Preview settings are saved separately for every note.

Theme and reading-style changes update the current document without reloading it. Parsing runs off the UI thread; stale results are discarded. Preview never rewrites the stored Markdown. Notes over two million characters show an explicit preview-unavailable state and remain available in the editor.

Raw HTML is displayed as text, JavaScript is disabled, and remote images are only requested after clicking **Load image**. Local/relative image paths show a placeholder: workspace notes are stored internally, not as files with an attachment directory.

#### Linking Notes and Tasks

- Notes and tasks use a many-to-many relationship: every note can link to multiple tasks and every task can link to multiple notes.
- Add multiple task links from the note editor; its linked-task menu shows the current count and provides separate Open and Unlink actions.
- Select or clear multiple note links from the editor's searchable list, with folder labels and preserved selections while filtering.
- Event details display links that open the linked notes directly in the Notes section.
- Note cards summarize all available linked tasks.
- Open linked notes from Tasks, Calendar entries, the calendar agenda, or the task dialog.
- Deleting a task removes only that task's links without deleting notes or affecting their links to other tasks.

### Agenda Sidebar

- Toggle the agenda panel from the top toolbar.
- Monthly mini-calendar with previous/next month navigation.
- Highlights the selected date and the current day.
- Displays tasks for the selected day or week.
- Shows at most 50 appointment cards, with a link to the full Agenda for larger schedules.
- Click an agenda item to open its task.
- Open notes linked to agenda tasks.

### Themes

Four themes are available:

- Dark
- Light
- Blue-Gray
- Gray Blue

The last selected theme is stored per account and restored when that account opens the application again. Themes are applied consistently to the main interface, dialogs, calendar, notes editor, Markdown Preview, and syntax highlighting.

The four palettes share a restrained workspace style: compact metric strips, lighter list rows, consistent spacing, and visible keyboard focus. Shared presentation lives in `css/workspace.css`; each theme supplies its own color tokens.

From the top-right **account menu → App icon**, choose **Book (original)** or **V (modern)**. The original book image is preserved. The choice is saved per account and immediately updates the in-app mark and open window icons, with Dock support where available. Packaged executable, installer and pinned shortcut icons remain platform/package-managed; they are not rewritten by this setting.

### Profile Picture

- Select PNG, JPG, or JPEG images.
- Maximum upload size: 10 MB.
- Supported dimensions range from 300×300 to 20,000×20,000 pixels.
- Interactive circular crop with drag and zoom controls.
- The optimized master image is stored as PNG at up to 1024×1024 pixels.
- Display renditions account for HiDPI output scaling.
- The remembered account avatar is preloaded during startup.
- One master and up to two disposable display renditions are retained.

## Keyboard and Mouse Reference

| Context | Action |
|---|---|
| Anywhere in the workspace | `Ctrl+K` / `Cmd+K` opens global search and quick actions |
| Anywhere in the workspace | `Ctrl+Shift+N` / `Cmd+Shift+N` creates a task |
| Anywhere in the workspace | `F1` opens help and shortcuts |
| Outside text editors | Ctrl/Cmd+Z undoes; Ctrl/Cmd+Shift+Z or Ctrl/Cmd+Y redoes |
| Global search | `Up` / `Down` selects, `Enter` opens, `Esc` closes |
| Home | Click a task or contact to open its editor; keyboard users can use `Tab` and `Enter` / `Space` |
| Contacts | Click a row to open its profile (Quick edit off) |
| Contacts | Click a cell to edit it in place (Quick edit on) |
| Contacts | Right-click a row to open the pop-up editor (Quick edit on) |
| Contacts | `Esc` cancels an in-place edit; `Enter` saves it |
| Contacts | Click a column label to rename it |
| Contacts | Drag a column header to reorder it |
| Contacts | Right-click a custom column label to remove the field |
| Contacts | `Ctrl+C` / `Cmd+C` copies selected contacts |
| Calendar | Click an event for details; Tab + Enter/Space also opens it |
| Calendar | Drag empty space to create a range; N creates an event, T returns to today |
| Calendar | Esc cancels an active gesture; Alt uses exact-minute movement |
| Calendar | Drag a task to reschedule it |
| Calendar | Drag the lower edge to change duration |
| Calendar | `Ctrl` / `Cmd` + mouse wheel changes zoom |
| Calendar | `Ctrl+0` / `Cmd+0` resets zoom |
| Tasks | Click a task row to edit it |
| Notes | Drag a note card to reorder it |
| Notes | Drag a note card onto a folder to move it |
| Markdown | `Ctrl+B` / `Cmd+B` inserts bold syntax |
| Markdown | `Ctrl+I` / `Cmd+I` inserts italic syntax |
| Markdown | Tab inserts four spaces |

## Local Persistence and Recovery

VoidReach stores application data under `.voidreach-crm` in the current user's home directory:

```text
~/.voidreach-crm/
├── users.properties                         # accounts, credentials, avatar name, preferred theme
├── users.properties.bak                     # previous atomic revision when available
├── session.properties                       # remembered account email only
├── data/
│   ├── <account-id>.properties              # contacts, custom fields, tasks, notes, ordering, links, view preferences
│   └── <account-id>.properties.bak          # previous atomic workspace revision
├── avatars/
│   ├── <account-id>-<uuid>.png              # authoritative cropped master
│   └── <account-id>-<uuid>.png.<size>.rendition.png
└── backup/<account-id>/
    └── crm-data-<timestamp>.properties      # rotating automatic workspace snapshots
```

- Workspace saves are debounced and performed away from the JavaFX UI thread.
- The latest state, including debounced note edits, is flushed before export, sign-out and window close. Failed final saves keep the workspace open for retry.
- Primary files are written atomically.
- The application attempts recovery from `.bak` when the primary revision cannot be read.
- Corrupt records are isolated in `.corrupt.properties` files so valid records can still load when possible.
- Automatic snapshots run every two minutes while an account workspace is open.
- Up to three automatic snapshots are retained per account.

- **History** keeps 25 session undo/redo steps. Text editors keep their own native undo shortcuts.
- **Recently deleted** persists contacts, tasks/events and notes across restarts, including linked-note restoration for tasks and deleted recurrence portions.
- **Settings → Backups & recovery** lists snapshots, creates manual checkpoints and previews/restores readable backups. Import/restore first keeps a checkpoint of the current readable workspace.
- Manual checkpoints are not automatically pruned. Automatic backups still rotate to three copies.
- A wholly unreadable workspace opens read-only: it is never replaced automatically by an empty saved workspace. Damaged imports report skipped records before confirmation.
- Workspace schema v2 reads legacy v1 files and adds explicit metadata for schedules, contact history, preferences and trash. Older desktop/Android versions may not understand these new fields; use matching versions for full-fidelity interchange.
- Local encryption is optional, as described above. Incomplete setup is not reported as protected; retry from Local protection. Managed storage is encrypted only after setup succeeds, while portable exports are intentionally plaintext.

## Requirements

- JDK 26; the project is currently configured for Java 26.
- Apache Maven 3.9 or newer.
- macOS, Windows, or Linux with JavaFX support.

Maven resolves JavaFX 26.0.1, RichTextFX 0.11.7, Ikonli, and image-processing dependencies.

Confirm that Maven uses the correct JDK:

```bash
java -version
mvn -version
```

If Maven selects a different JDK, update `JAVA_HOME` before building or running VoidReach.

## Running the Application

From the Maven module:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn clean javafx:run
```

On macOS or Linux, the helper script runs `mvn javafx:run` without the `clean` phase, reusing the previous build output:

```bash
cd VoidReach-CRM-Final-No-FatJar
./run.sh
```

For IntelliJ IDEA, import `VoidReach-CRM-Final-No-FatJar/pom.xml` as a Maven project and run `com.crm.app.AppLauncher`.

## Tests

Run the complete unit-test suite:

```bash
cd VoidReach-CRM-Final-No-FatJar
mvn clean test
```

Run the bounded-memory large-avatar integration test:

```bash
mvn verify -Pavatar-large-image
```

The tests cover contact/overview behavior, task/note models, recurrence and overlap layout, ICS round trips and unsupported rules, reminders, workload insights, history, schema migration, atomic saves, close/export failure handling, encryption/tamper/lock/recovery behavior, themes and avatar processing.

Search ranking, cross-record matches, account isolation, and reminder time boundaries are also covered. To run the optional JavaFX UI smoke test on a machine with a graphical desktop:

```bash
mvn -Dtest=WorkspaceUiIT test
```

It renders all seven sections and all four Calendar views in four themes. A shown JavaFX window with an isolated test account exercises retained invalid drafts, fixed headers, overlapping events, recurrence splitting/deletion, undo/redo, trash restoration with note links, Focus mode, final-save flushing, a 10,000-task paginated list, and dense-calendar navigation with a bounded mini-agenda. It also checks search, compact-window layouts, and loading of the sign-in/recovery and splash screens. Screenshots are written to `target/ui-preview/`. It uses synthetic records and isolated data directories under `target/`, without opening your account.

## Native Packaging

Native application images must be created on their target operating system. Packaging requires a JDK 26+ whose `bin` directory (containing `jpackage`) is on `PATH`, plus Apache Maven; both scripts verify this before building.

### macOS

```bash
cd VoidReach-CRM-Final-No-FatJar
./scripts/package-macos.sh
```

The application image is created at `target/packages/macos/VoidReach.app`.

### Windows

Run the script from **PowerShell** (not from `cmd.exe`). The default PowerShell execution policy blocks local scripts, so launch it with a per-run bypass:

```powershell
cd VoidReach-CRM-Final-No-FatJar
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

If your execution policy already allows local scripts, `./scripts/package-windows.ps1` works too.

The application image is created at `target\packages\windows\VoidReach\` and is started with `VoidReach.exe` inside that folder.

Packages are written under `target/packages/`. Packaging scripts do not run the test suite automatically, so run `mvn clean test` before distributing a build.

Platform icons are located at:

- `src/main/packaging/macos/VoidReach-v2.icns`
- `src/main/packaging/windows/VoidReach-v2.ico`

The resolution-independent V mark is defined in `com.crm.view.BrandMark`; the original book image remains available through the account icon preference. In-app marks follow the active account's choice. Packaging scripts still select the V native assets. To regenerate PNG, multi-resolution ICO and ICNS assets under `target/brand-assets/`, run this separate graphical test:

```bash
mvn -Dtest=BrandAssetsIT test
```

Review generated assets before replacing the packaging files. Windows packaging is verified locally; macOS and Linux need native testing on their respective systems.

## Technology

- Java 26
- JavaFX 26.0.1 (`javafx-controls`, `javafx-fxml`, `javafx-swing`)
- RichTextFX 0.11.7
- Ikonli 12.3.1 (FontAwesome 5 pack)
- TwelveMonkeys ImageIO 3.13.1
- Maven
- JUnit Jupiter 5.11.4

## Project Structure

```text
VoidReach-CRM-Calendar-Task-Note/
├── README.md
├── LICENSE
├── sample/                                 # interface screenshots
└── VoidReach-CRM-Final-No-FatJar/
    ├── pom.xml
    ├── run.sh
    ├── scripts/
    │   ├── package-macos.sh
    │   └── package-windows.ps1
    └── src/
        ├── main/
        │   ├── java/com/crm/
        │   │   ├── app/                    # launcher and JavaFX application lifecycle
        │   │   ├── controller/             # login, main, navigation, splash, account, overview,
        │   │   │                           #   contacts, calendar, tasks, notes
        │   │   ├── model/                  # contacts, tasks, notes, accounts, workspace snapshots
        │   │   ├── repository/             # atomic local storage, recovery, quarantine, backups
        │   │   └── service/                # authentication, sessions, dialogs, themes, avatars,
        │   │                               #   Markdown code lexer
        │   ├── packaging/                  # native platform icons (VoidReach-v2.icns, VoidReach-v2.ico)
        │   └── resources/
        │       ├── com/crm/view/           # FXML layouts (LoginView, MainView, SplashScreen)
        │       ├── css/                    # Light, Dark, Blue-gray, and Gray Blue themes
        │       └── images/                 # application graphics
        └── test/java/com/crm/              # unit and integration tests
```

## Repository Purpose and Usage Restrictions

The repository is public only so visitors can inspect the source code and evaluate the author's software-development work through GitHub. It is **not open source** and grants no right to reuse the code or any portion of it.

Without the author's prior written permission, you may not:

- use a fork or other reproduction of the repository as the basis for another project;
- copy, modify, adapt, or create derivative works from the code, in whole or in part;
- use the code in your own or a third party's project, product, service, coursework submission, internal tool, or production system;
- run, distribute, sublicense, sell, or otherwise exploit the application or its source code for commercial, professional, or business purposes.

Commercial or professional use of VoidReach requires the purchase of an authorized commercial version or a separate written commercial license from the author.

## License

Bundled Inter and JetBrains Mono font files retain their SIL Open Font License 1.1; see [font provenance and licenses](VoidReach-CRM-Final-No-FatJar/src/main/resources/fonts/README.md). Third-party dependencies retain their respective licenses. The repository's proprietary terms apply to the author's application code, not to those third-party works.

Copyright © 2026 Antonio Dicorato. All rights reserved. See [LICENSE](LICENSE) for the complete terms governing this repository.
