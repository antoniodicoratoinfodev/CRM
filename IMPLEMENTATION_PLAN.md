# VoidReach product upgrade

Approved scope: the general product review and the dedicated Calendar plan.
Existing user changes are preserved. Verification uses isolated synthetic workspaces.

## Work packages

- [x] Reliable save/close/export lifecycle, truthful save feedback, recoverable deletion and undo/redo.
- [x] Backward-compatible model: optional scheduling/deadlines, priorities/status, all-day/multi-day events, recurrence, reminders, contact links.
- [x] Calendar correctness: overlaps, retained invalid drafts, reversible gestures.
- [x] Calendar UI: fixed headers, lighter grid, current-time marker, clear toolbar, working-hours/week-start/snap preferences.
- [x] Calendar interaction and views: range creation, quick details, keyboard, Day/Week/Month/Agenda.
- [x] Recurrence editing, persistent reminder snooze, ICS import/export.
- [x] Contact detail, dated interaction history, linked tasks/notes, follow-up creation.
- [x] Focused notes, templates, contextual sidebar, coherent shared controls and visual identity.
- [x] Actionable Home/Dashboard and efficient refreshes.
- [x] Backup management/restore, local-account recovery and protection, faster startup.
- [x] Unit/integration/visual verification, migrated fixtures, documentation and screenshots.

## Follow-up: Markdown reading and typography

- [x] Critical review of Markdown semantics and separation between source and reading appearance.
- [x] Selectable CommonMark preview, code copy, safe explicit links/images, asynchronous rendering and recoverable failure states.
- [x] Bundled Inter/JetBrains Mono, centralized typography, real Medium/Semibold faces and per-screen refinements.
- [x] Four-theme visual checks, narrow note layout, actual copy/link interactions and regression coverage.
- [x] Findings, tradeoffs and maintenance guidance in [TYPOGRAPHY_PREVIEW_REVIEW.md](TYPOGRAPHY_PREVIEW_REVIEW.md).

## Original acceptance

Legacy workspaces must remain readable, with old records preserved. No real account data is
used for tests. Invalid forms remain open. Closing waits for pending saves and reports failures.
Simultaneous appointments remain discoverable. Recurrence expansion is bounded and tested.
Reminder limitations with the application closed are explicit. ICS never silently discards
unsupported recurrence rules. Changes are checked at narrow widths and in all four themes.

## Verification — 2026-09-06

- `mvn -B -q package`: 98 unit tests, no failures or errors, including icon preference persistence and selectable snap behavior.
- `mvn -B -q -Dtest=WorkspaceUiIT test`: four passing UI scenarios covering all four themes, all Calendar views, compact layouts, sign-in/recovery/splash loading, mouse drag/resize snapping, icon selection and real shown-window workflows.
- Dense-data checks cover a 10,000-task list/calendar, a 50-card mini-agenda limit and a 20,000-event overlap layout.
- `mvn -B -q -Dtest=BrandAssetsIT test`: native asset generation passed separately.
- Windows `jpackage --type app-image` completed; the packaged application JAR matches the final verified build by SHA-256.
- Updated gallery in `sample/screenshots/`; implementation summary and handoff in [UPGRADE_REPORT.md](UPGRADE_REPORT.md).

The checked items refer to the implemented scope and local verification, not certification on every platform. macOS/Linux native behavior, background OS notifications, full calendar-provider synchronization and an independent security audit are not claimed. Encryption remains opt-in and no real account was opened or migrated during testing.
