package com.crm.controller;

import com.crm.model.Note;
import com.crm.model.NoteFolder;
import com.crm.model.Task;
import com.crm.model.Contact;
import com.crm.model.CalendarPreferences;
import com.crm.service.CalendarOccurrenceService;
import com.crm.service.CalendarOccurrenceService.Occurrence;
import com.crm.service.ICalendarService;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns calendar state, task editing, timeline rendering, zoom, and the calendar sidebar. */
public final class CalendarController {
    private static final int MINI_CALENDAR_COLUMNS = 7;
    private static final double MINI_CALENDAR_MAX_CELL_SIZE = 40;
    private static final double ZOOM_STEP = 0.1;
    private static final double MIN_ZOOM = 0.75;
    private static final double MAX_ZOOM = 3.0;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double MIN_TIMELINE_WIDTH = 320.0;

    private final VBox calendarView;
    private final AnchorPane timeLabelsContainer;
    private final AnchorPane timelineArea;
    private final HBox calendarContentRow;
    private final ScrollPane scrollPane;
    private final DatePicker datePicker;
    private final ComboBox<String> viewModeCombo;
    private final Label selectedPeriodLabel;
    private final Label zoomLabel;
    private final Label miniMonthYearLabel;
    private final GridPane miniCalendarGrid;
    private final Label activitiesTitle;
    private final VBox upcomingActivitiesList;
    private final ThemeService themeService;
    private final DialogService dialogService;
    private final Runnable dataChanged;
    private final Runnable showCalendar;
    private final Map<LocalDate, List<Task>> tasksByDate = new HashMap<>();
    private NoteIntegration noteIntegration = NoteIntegration.EMPTY;
    private CalendarBoard board;
    private CalendarPreferences preferences = CalendarPreferences.DEFAULT;
    private java.util.function.Supplier<List<Contact>> contactSupplier = List::of;
    private java.util.function.BiConsumer<LocalDate, Task> archiveDeleted = (date, task) -> { };
    public void setArchiveDeleted(java.util.function.BiConsumer<LocalDate, Task> action) { archiveDeleted = action; }

    private double zoom = DEFAULT_ZOOM;
    private PauseTransition resizeDebounce;
    private boolean calendarOpening;
    private boolean applyingState;
    private Scene shortcutScene;
    private String viewMode = "Day";
    private LocalDate weekStartDate;
    private YearMonth currentMiniMonth;

    public CalendarController(VBox calendarView, AnchorPane timeLabelsContainer,
                              AnchorPane timelineArea, HBox calendarContentRow, ScrollPane scrollPane,
                              DatePicker datePicker, ComboBox<String> viewModeCombo,
                              Label selectedPeriodLabel, Label zoomLabel,
                              Label miniMonthYearLabel, GridPane miniCalendarGrid,
                              Label activitiesTitle, VBox upcomingActivitiesList, ThemeService themeService,
                              DialogService dialogService, Runnable dataChanged,
                              Runnable showCalendar) {
        this.calendarView = Objects.requireNonNull(calendarView);
        this.timeLabelsContainer = Objects.requireNonNull(timeLabelsContainer);
        this.timelineArea = Objects.requireNonNull(timelineArea);
        this.calendarContentRow = Objects.requireNonNull(calendarContentRow);
        this.scrollPane = Objects.requireNonNull(scrollPane);
        this.datePicker = Objects.requireNonNull(datePicker);
        this.viewModeCombo = Objects.requireNonNull(viewModeCombo);
        this.selectedPeriodLabel = Objects.requireNonNull(selectedPeriodLabel);
        this.zoomLabel = Objects.requireNonNull(zoomLabel);
        this.miniMonthYearLabel = Objects.requireNonNull(miniMonthYearLabel);
        this.miniCalendarGrid = Objects.requireNonNull(miniCalendarGrid);
        this.activitiesTitle = Objects.requireNonNull(activitiesTitle);
        this.upcomingActivitiesList = Objects.requireNonNull(upcomingActivitiesList);
        this.themeService = Objects.requireNonNull(themeService);
        this.dialogService = Objects.requireNonNull(dialogService);
        this.dataChanged = Objects.requireNonNull(dataChanged);
        this.showCalendar = Objects.requireNonNull(showCalendar);
    }

    public void initialize() {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        currentMiniMonth = YearMonth.from(today);
        weekStartDate = weekStart(today);
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) refreshForSelectedDate(newValue);
        });
        installCalendarContentClip();
        setupMiniCalendarLayout();
        miniCalendarGrid.widthProperty().addListener((observable, oldWidth, newWidth) ->
                resizeMiniCalendarCells());
        setupViewModeCombo();
        setupZoomControls();
        board = new CalendarBoard(this, calendarView, timelineArea, timeLabelsContainer, calendarContentRow,
                scrollPane, datePicker, viewModeCombo, selectedPeriodLabel, zoomLabel);
        updateZoomLabel();
        render();
        updateSidebar();
    }

    private void installCalendarContentClip() {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(26);
        clip.setArcHeight(26);
        clip.widthProperty().bind(scrollPane.widthProperty());
        clip.heightProperty().bind(scrollPane.heightProperty());
        scrollPane.setClip(clip);
    }

    public void setNoteIntegration(NoteIntegration noteIntegration) {
        this.noteIntegration = noteIntegration == null ? NoteIntegration.EMPTY : noteIntegration;
        if (currentMiniMonth != null) {
            render();
            updateSidebar();
        }
    }

    public void applyState(Map<LocalDate, List<Task>> source, LocalDate selectedDate,
                           String selectedViewMode, double selectedZoom) {
        applyingState = true;
        try {
            tasksByDate.clear();
            if (source != null) source.forEach((date, tasks) -> tasksByDate.put(date, new ArrayList<>(tasks)));
            zoom = clamp(selectedZoom, MIN_ZOOM, MAX_ZOOM);
            updateZoomLabel();
            viewMode = List.of("Day", "Week", "Month", "Agenda").contains(selectedViewMode) ? selectedViewMode : "Day";
            LocalDate date = selectedDate == null ? LocalDate.now() : selectedDate;
            viewModeCombo.setValue(viewMode);
            datePicker.setValue(date);
            weekStartDate = weekStart(date);
            currentMiniMonth = YearMonth.from(date);
            render();
            updateSidebar();
        } finally {
            applyingState = false;
        }
    }

    public Map<LocalDate, List<Task>> tasksSnapshot() {
        Map<LocalDate, List<Task>> copy = new HashMap<>();
        tasksByDate.forEach((date, tasks) -> copy.put(date, new ArrayList<>(tasks)));
        return copy;
    }

    public LocalDate selectedDate() { return datePicker.getValue(); }
    public String viewMode() { return viewMode; }
    public double zoom() { return zoom; }
    CalendarPreferences preferences() { return preferences; }
    void setSnap(CalendarPreferences.Snap snap) {
        preferences = preferences.withSnap(snap); render(); notifyDataChanged();
    }
    public void applyPreferences(Map<String, String> values) { preferences = CalendarPreferences.from(values); }
    public Map<String, String> preferencesSnapshot() { return preferences.values(); }
    public void setContacts(java.util.function.Supplier<List<Contact>> contacts) { contactSupplier = contacts; }
    void selectDay(LocalDate date) { viewModeCombo.setValue("Day"); selectDate(date); showCalendar.run(); }
    void showAgenda(LocalDate date) { viewModeCombo.setValue("Agenda"); selectDate(date); showCalendar.run(); }
    void createEvent(LocalDate date, int start, int duration) { editItem(date, null, start, duration, "", false); }
    public void createFollowUp(Contact contact, LocalDate due) {
        Task draft = Task.scheduled(java.util.UUID.randomUUID().toString(), "Follow up with " + contact.nameProperty().get(), "", 0, 60, "Blue", false);
        draft.setScheduled(false); draft.setDueDate(due); draft.setContactId(contact.getId());
        new CalendarTaskEditor(themeService, noteIntegration, contactSupplier.get()).show(draft, due, 0, 60, "", true)
                .filter(result -> !result.delete()).ifPresent(result -> commitEdit(due, null, result, null));
    }

    private void editItem(LocalDate date, Task original, int start, int duration, String description, boolean todo) {
        new CalendarTaskEditor(themeService, noteIntegration, contactSupplier.get()).show(original, date, start, duration, description, todo)
                .ifPresent(result -> commitEdit(date, original, result, null));
    }

    private LocalDate sourceDate(Task task) {
        return tasksByDate.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(item -> item.getId().equals(task.getId())))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void commitEdit(LocalDate occurrence, Task original, CalendarTaskEditor.Result result, String forcedScope) {
        LocalDate source = original == null ? null : sourceDate(original);
        String scope = "Entire series";
        if (original != null && original.getFrequency() != Task.Frequency.NONE) {
            if (forcedScope != null) scope = forcedScope;
            else {
                ChoiceDialog<String> choice = new ChoiceDialog<>("This occurrence", "This occurrence", "This and following", "Entire series");
                choice.setTitle(result.delete() ? "Delete recurring event" : "Update recurring event");
                choice.setHeaderText("Which events should change?");
                choice.setContentText("This occurrence becomes independent; the other events stay unchanged.");
                themeService.applyTo(choice);
                Optional<String> selected = choice.showAndWait();
                if (selected.isEmpty()) { render(); return; }
                scope = selected.get();
            }
        }
        Task replacement = result.task(); LocalDate target = result.date();
        boolean detached = original != null && original.getFrequency() != Task.Frequency.NONE && !scope.equals("Entire series");
        if (source != null && detached) {
            if (scope.equals("This occurrence")) {
                if (result.delete()) {
                    Task archived = com.crm.service.WorkspaceHistoryService.archiveTask(original, noteIntegration.notes()).copyWithId(java.util.UUID.randomUUID().toString());
                    archived.setFrequency(Task.Frequency.NONE); archived.setRepeatCount(0); archived.setRepeatUntil(null); archived.clearExclusions();
                    archiveDeleted.accept(occurrence, archived);
                }
                original.exclude(occurrence);
                if (!result.delete()) {
                    replacement = replacement.copyWithId(java.util.UUID.randomUUID().toString());
                    replacement.setFrequency(Task.Frequency.NONE); replacement.clearExclusions(); replacement.setRepeatCount(0); replacement.setRepeatUntil(null);
                }
            } else if (occurrence.equals(source)) {
                removeTask(source, original); detached = false;
            } else {
                LocalDate previousUntil = original.getRepeatUntil(); int previousCount = original.getRepeatCount();
                int used = CalendarOccurrenceService.countBefore(original, source, occurrence);
                if (result.delete()) {
                    Task archived = com.crm.service.WorkspaceHistoryService.archiveTask(original, noteIntegration.notes()).copyWithId(java.util.UUID.randomUUID().toString());
                    if (previousCount > 0) archived.setRepeatCount(Math.max(1, previousCount - used));
                    archiveDeleted.accept(occurrence, archived);
                }
                original.setRepeatCount(0); original.setRepeatUntil(occurrence.minusDays(1));
                if (!result.delete()) {
                    replacement = replacement.copyWithId(java.util.UUID.randomUUID().toString());
                    // Preserve explicit edits to the recurrence end; otherwise retain remaining COUNT.
                    if (previousCount > 0 && replacement.getRepeatCount() == previousCount)
                        replacement.setRepeatCount(Math.max(1, previousCount - used));
                    long shift = java.time.temporal.ChronoUnit.DAYS.between(occurrence, target);
                    replacement.clearExclusions();
                    for (LocalDate excluded : original.getExcludedDates())
                        if (!excluded.isBefore(occurrence)) replacement.exclude(excluded.plusDays(shift));
                }
            }
        } else if (source != null) {
            removeTask(source, original);
            if (original.getFrequency() != Task.Frequency.NONE && !result.delete()) {
                long shift = java.time.temporal.ChronoUnit.DAYS.between(occurrence, target);
                target = source.plusDays(shift);
                if (shift != 0) {
                    replacement.clearExclusions();
                    for (LocalDate excluded : original.getExcludedDates()) replacement.exclude(excluded.plusDays(shift));
                }
            }
        }
        if (original != null && !detached) unlinkNotes(original.getId());
        if (!result.delete()) {
            addTask(target, replacement);
            for (Note note : noteIntegration.notes()) if (result.notes().contains(note.getId())) note.linkTask(replacement.getId());
        }
        render(); updateSidebar(); notifyDataChanged();
    }

    void moveOccurrence(Occurrence occurrence, LocalDate date, int start, int duration) {
        Task original = occurrence.task();
        Task replacement = Task.scheduled(original.getId(), original.getTitle(), original.getDescription(), start, duration, original.getColor(), original.isCompleted());
        replacement.applyMetadata(original.metadata());
        Set<String> links = new HashSet<>(); noteIntegration.notesForTask(original.getId()).forEach(note -> links.add(note.getId()));
        commitEdit(occurrence.date(), original, new CalendarTaskEditor.Result(date, replacement, links, false), null);
    }

    void showDetails(Occurrence entry) {
        Task task = entry.task(); Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Event details"); themeService.applyTo(dialog);
        Label title = new Label(task.getTitle()); title.getStyleClass().add("section-title"); title.setWrapText(true);
        Label schedule = new Label(task.isAllDay() ? "All day · " + entry.date() : entry.start() + " → " + entry.end()); schedule.setWrapText(true);
        Label description = new Label(task.getDescription()); description.setWrapText(true);
        VBox content = new VBox(12, title, schedule, new Label(task.getPriority() + " priority · " + task.getStatus()), description);
        contactSupplier.get().stream().filter(c -> c.getId().equals(task.getContactId())).findFirst()
                .ifPresent(contact -> content.getChildren().add(new Label("Contact: " + contact.nameProperty().get())));
        for (Note note : noteIntegration.notesForTask(task.getId())) {
            Button link = new Button(note.getTitle()); link.getStyleClass().add("text-button");
            link.setOnAction(e -> { dialog.close(); noteIntegration.openNote(note.getId()); }); content.getChildren().add(link);
        }
        content.getStyleClass().add("event-editor"); content.setPrefWidth(420); dialog.getDialogPane().setContent(content);
        ButtonType edit = new ButtonType("Edit", ButtonBar.ButtonData.OK_DONE), duplicate = new ButtonType("Duplicate", ButtonBar.ButtonData.OTHER),
                delete = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(edit, duplicate, delete, ButtonType.CLOSE);
        dialog.showAndWait().ifPresent(result -> {
            if (result == edit) editTask(entry.date(), task);
            else if (result == delete) deleteTask(entry.date(), task);
            else if (result == duplicate) {
                Task copy = task.copyWithId(java.util.UUID.randomUUID().toString()); copy.setFrequency(Task.Frequency.NONE); copy.clearExclusions();
                new CalendarTaskEditor(themeService, noteIntegration, contactSupplier.get()).show(copy, entry.date(), copy.getStartMin(), copy.getDuration(), copy.getDescription(), false)
                        .filter(value -> !value.delete()).ifPresent(value -> commitEdit(entry.date(), null, value, null));
            }
        });
    }

    void showPreferences() {
        Dialog<CalendarPreferences> dialog = new Dialog<>(); dialog.setTitle("Calendar preferences"); themeService.applyTo(dialog);
        ComboBox<java.time.DayOfWeek> first = new ComboBox<>(FXCollections.observableArrayList(java.time.DayOfWeek.values())); first.setValue(preferences.firstDay());
        TextField start = new TextField(CalendarBoard.time(preferences.workStart())), end = new TextField(CalendarBoard.time(preferences.workEnd()));
        ComboBox<CalendarPreferences.Snap> snap = new ComboBox<>(FXCollections.observableArrayList(CalendarPreferences.Snap.values()));
        snap.setId("calendarSnapPreference"); snap.setValue(preferences.snapMode());
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true);
        VBox content = new VBox(10, new Label("First day of the week"), first, new Label("Working hours (HH:mm)"), new HBox(8, start, end),
                new Label("Drag and resize snap (hold Alt for 1 minute)"), snap, error); content.getStyleClass().add("event-editor");
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        CalendarPreferences[] accepted = new CalendarPreferences[1];
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try { accepted[0] = new CalendarPreferences(first.getValue(), CalendarTaskEditor.parseTime(start.getText(), false), CalendarTaskEditor.parseTime(end.getText(), true), snap.getValue().minutes()); }
            catch (RuntimeException invalid) { error.setText(invalid.getMessage()); e.consume(); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? accepted[0] : null);
        dialog.showAndWait().ifPresent(value -> { preferences = value; weekStartDate = weekStart(selectedDate()); render(); updateSidebar(); notifyDataChanged(); });
    }

    void importCalendar() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser(); chooser.setTitle("Import calendar");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("iCalendar", "*.ics"));
        java.io.File file = chooser.showOpenDialog(calendarView.getScene().getWindow()); if (file == null) return;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                if (java.nio.file.Files.size(file.toPath()) > 20_000_000) throw new IllegalArgumentException("Calendar files must be under 20 MB.");
                return ICalendarService.read(java.nio.file.Files.readString(file.toPath()), java.time.ZoneId.systemDefault());
            } catch (java.io.IOException failure) { throw new IllegalStateException("The calendar file could not be read.", failure); }
        }).whenComplete((imported, failure) -> Platform.runLater(() -> {
            if (failure != null) { dialogService.showError("Import failed", failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage()); return; }
            String warnings = imported.warnings().isEmpty() ? "" : "\n\nSkipped events:\n" + String.join("\n", imported.warnings().stream().limit(12).toList());
            if (imported.count() == 0) { dialogService.showInfo("Nothing to import", "No supported events were found." + warnings); return; }
            if (!dialogService.confirmWarning("Import calendar", "Add " + imported.count() + " events? Existing matching IDs are kept unchanged." + warnings, "Import")) return;
            Set<String> ids = new HashSet<>(); tasksByDate.values().forEach(items -> items.forEach(item -> ids.add(item.getId())));
            imported.tasks().forEach((date, items) -> items.forEach(item -> { if (ids.add(item.getId())) addTask(date, item); }));
            render(); updateSidebar(); notifyDataChanged();
        }));
    }

    void exportCalendar() {
        if (!dialogService.confirmWarning("Export calendar", "ICS files are not encrypted. Appointments use local wall-clock time; tasks without a schedule are not included.", "Continue")) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser(); chooser.setTitle("Export calendar"); chooser.setInitialFileName("VoidReach-calendar.ics");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("iCalendar", "*.ics"));
        java.io.File file = chooser.showSaveDialog(calendarView.getScene().getWindow()); if (file == null) return;
        Map<LocalDate, List<Task>> copy = new HashMap<>(); tasksByDate.forEach((date, items) -> copy.put(date, items.stream().map(Task::copy).toList()));
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { ICalendarService.writeFile(file.toPath(), copy, java.time.ZoneId.systemDefault()); }
            catch (java.io.IOException failure) { throw new IllegalStateException("The calendar file could not be written.", failure); }
        }).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (failure != null) dialogService.showError("Export failed", "The calendar could not be exported. Your workspace is unchanged.");
            else dialogService.showInfo("Calendar exported", "Appointments were exported in local time. Tasks without a schedule stay in the workspace.");
        }));
    }

    public void zoomIn() { handleZoom(true, viewportCenterContentY()); }
    public void zoomOut() { handleZoom(false, viewportCenterContentY()); }
    public void resetZoom() { applyZoom(DEFAULT_ZOOM, viewportCenterContentY()); }

    public void createTask(LocalDate date) {
        editItem(date == null ? LocalDate.now() : date, null, 9 * 60, 60, "", true);
    }

    public void editTask(LocalDate date, Task task) {
        if (date == null || task == null) return;
        selectDate(date);
        showTaskDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
    }

    public void deleteTask(LocalDate date, Task task) {
        if (date == null || task == null) return;
        commitEdit(date, task, new CalendarTaskEditor.Result(date, task, Set.of(), true), null);
    }

    public void setTaskCompleted(LocalDate date, Task task, boolean completed) {
        if (date == null || task == null || task.isCompleted() == completed) return;
        Task replacement = task.copy(); replacement.setCompleted(completed);
        Set<String> links = new HashSet<>(); noteIntegration.notesForTask(task.getId()).forEach(note -> links.add(note.getId()));
        commitEdit(date, task, new CalendarTaskEditor.Result(date, replacement, links, false), "This occurrence");
    }

    public void showTaskInCalendar(LocalDate date) {
        if (date == null) return;
        selectDate(date);
        showCalendar.run();
    }

    public void refreshTheme() {
        render();
        updateSidebar();
    }

    /** Refreshes note titles and link controls without changing calendar state or saving data. */
    public void refreshNoteLinks() {
        render();
        updateSidebar();
    }

    public void previousMiniMonth() {
        currentMiniMonth = currentMiniMonth.minusMonths(1);
        updateSidebar();
    }

    public void nextMiniMonth() {
        currentMiniMonth = currentMiniMonth.plusMonths(1);
        updateSidebar();
    }

    /** Gives weekdays and dates the same responsive column at every agenda width. */
    private void setupMiniCalendarLayout() {
        miniCalendarGrid.setMinWidth(0);
        miniCalendarGrid.setMaxWidth(Double.MAX_VALUE);
        miniCalendarGrid.getColumnConstraints().clear();
        for (int column = 0; column < MINI_CALENDAR_COLUMNS; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / MINI_CALENDAR_COLUMNS);
            constraints.setHalignment(HPos.CENTER);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            miniCalendarGrid.getColumnConstraints().add(constraints);
        }
    }

    public void today() { selectDate(LocalDate.now()); }

    public void previousPeriod() {
        if (viewMode.equals("Month")) { selectDate(datePicker.getValue().minusMonths(1)); return; }
        if (viewMode.equals("Agenda")) { selectDate(datePicker.getValue().minusDays(30)); return; }
        LocalDate target = viewMode.equals("Day")
                ? datePicker.getValue().minusDays(1) : weekStartDate.minusWeeks(1);
        selectDate(target);
    }

    public void nextPeriod() {
        if (viewMode.equals("Month")) { selectDate(datePicker.getValue().plusMonths(1)); return; }
        if (viewMode.equals("Agenda")) { selectDate(datePicker.getValue().plusDays(30)); return; }
        LocalDate target = viewMode.equals("Day")
                ? datePicker.getValue().plusDays(1) : weekStartDate.plusWeeks(1);
        selectDate(target);
    }

    private void setupZoomControls() {
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        resizeDebounce = new PauseTransition(Duration.millis(120));
        resizeDebounce.setOnFinished(event -> {
            render();
            scrollPane.setHvalue(0);
        });
        scrollPane.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
            if (Math.abs(oldBounds.getWidth() - newBounds.getWidth()) <= 1 || !calendarView.isVisible()) return;
            if (calendarOpening) {
                calendarOpening = false;
                resizeDebounce.stop();
                render();
                scrollPane.setHvalue(0);
            } else {
                resizeDebounce.playFromStart();
            }
        });
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!calendarView.isVisible()) return;
            if (!event.isControlDown() && !event.isMetaDown()) return;
            event.consume();
            Point2D pivot = timelineArea.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (event.getDeltaY() > 0) handleZoom(true, pivot.getY());
            else if (event.getDeltaY() < 0) handleZoom(false, pivot.getY());
        });
        scrollPane.addEventFilter(ZoomEvent.ZOOM, event -> {
            if (!calendarView.isVisible() || event.getZoomFactor() <= 0) return;
            event.consume();
            Point2D pivot = timelineArea.sceneToLocal(event.getSceneX(), event.getSceneY());
            applyZoom(clamp(zoom * event.getZoomFactor(), MIN_ZOOM, MAX_ZOOM), pivot.getY());
        });
        timelineArea.setFocusTraversable(true);
        installShortcutFilterWhenSceneReady();
        calendarView.visibleProperty().addListener((observable, oldValue, visible) -> {
            if (visible) {
                calendarOpening = true;
                resizeDebounce.stop();
                timelineArea.requestFocus();
                scrollPane.setHvalue(0);
            } else {
                calendarOpening = false;
                resizeDebounce.stop();
            }
        });
    }

    private void installShortcutFilterWhenSceneReady() {
        calendarView.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
            shortcutScene = newScene;
            if (newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
        });
        if (calendarView.getScene() != null) {
            shortcutScene = calendarView.getScene();
            shortcutScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
        }
    }

    private void handleCalendarShortcut(KeyEvent event) {
        if (!calendarView.isVisible()) return;
        if (calendarView.getScene() != null && calendarView.getScene().getFocusOwner() instanceof TextInputControl) return;
        if (!event.isControlDown() && !event.isMetaDown()) return;
        switch (event.getCode()) {
            case DIGIT0, NUMPAD0 -> {
                event.consume();
                resetZoom();
            }
            case PLUS, ADD, EQUALS -> {
                event.consume();
                zoomIn();
            }
            case MINUS, SUBTRACT -> {
                event.consume();
                zoomOut();
            }
            default -> { }
        }
    }

    private void handleZoom(boolean zoomIn, double pivotContentY) {
        double target = zoomIn ? Math.min(zoom + ZOOM_STEP, MAX_ZOOM) : Math.max(zoom - ZOOM_STEP, MIN_ZOOM);
        applyZoom(target, pivotContentY);
    }

    private void applyZoom(double target, double pivotContentY) {
        target = clamp(target, MIN_ZOOM, MAX_ZOOM);
        double oldZoom = zoom;
        if (Math.abs(oldZoom - target) < 0.0001) return;
        double contentHeight = calendarContentHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double oldScrollY = scrollY(contentHeight, viewportHeight);
        double pivotViewportY = clamp(pivotContentY - oldScrollY, 0, Math.max(0, viewportHeight));
        double stablePivotContentY = oldScrollY + pivotViewportY;
        double scaleFactor = target / oldZoom;
        zoom = target;
        updateZoomLabel();
        render();
        scrollPane.setHvalue(0);
        notifyDataChanged();
        Platform.runLater(() -> {
            calendarContentRow.applyCss();
            calendarContentRow.layout();
            scrollPane.applyCss();
            scrollPane.layout();
            double newScrollableHeight = Math.max(0, calendarContentHeight() - viewportHeight);
            if (newScrollableHeight > 0) {
                double topInset = timelineTopInset();
                double newScrollY = topInset + (stablePivotContentY - topInset) * scaleFactor - pivotViewportY;
                scrollPane.setVvalue(clamp(newScrollY / newScrollableHeight, 0, 1));
            } else scrollPane.setVvalue(0);
            scrollPane.setHvalue(0);
        });
    }

    private void updateZoomLabel() {
        zoomLabel.setText(Math.round(zoom * 100) + "%");
    }

    private double viewportCenterContentY() {
        double contentHeight = calendarContentHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        return scrollY(contentHeight, viewportHeight) + viewportHeight / 2;
    }

    private double scrollY(double contentHeight, double viewportHeight) {
        return scrollPane.getVvalue() * Math.max(0, contentHeight - viewportHeight);
    }

    private double calendarContentHeight() {
        return Math.max(timelineArea.getHeight(), timelineArea.getPrefHeight());
    }

    static double timelineWidthFor(double viewportWidth, double timeColumnWidth) {
        return Math.max(MIN_TIMELINE_WIDTH, viewportWidth - timeColumnWidth);
    }

    private double timelineTopInset() {
        return 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void setupViewModeCombo() {
        viewModeCombo.setItems(FXCollections.observableArrayList("Day", "Week", "Month", "Agenda"));
        viewModeCombo.setValue("Day");
        viewModeCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
            viewMode = newValue;
            if (newValue.equals("Week")) weekStartDate = weekStart(datePicker.getValue());
            render();
            updateSidebar();
            notifyDataChanged();
        });
    }

    LocalDate weekStart(LocalDate date) {
        return date.minusDays(Math.floorMod(date.getDayOfWeek().getValue() - preferences.firstDay().getValue(), 7));
    }

    private void addTask(LocalDate date, Task task) {
        tasksByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task);
    }

    private void removeTask(LocalDate date, Task task) {
        List<Task> tasks = tasksByDate.get(date);
        if (tasks == null) return;
        tasks.remove(task);
        if (tasks.isEmpty()) tasksByDate.remove(date);
    }

    private void render() { if (board != null) board.render(); }

    static double taskEntryWidth(double dayWidth, double margin) { return Math.max(0, dayWidth - margin * 2); }
    static double taskEntryHeight(int durationMinutes, double minuteHeight) { return Math.max(0, durationMinutes * minuteHeight); }
    static double taskTitleFontSize(double height) { return clamp(12 * Math.max(0, height) / 24, 4, 12); }
    static int taskDurationAfterResize(int duration, double pixels, double minuteHeight, int maximum) {
        return Math.max(1, Math.min(maximum, duration + (minuteHeight <= 0 ? 0 : (int)Math.round(pixels / minuteHeight))));
    }
    static boolean taskResizeHit(double x, double y, double left, double top, double width, double height) {
        return x >= left && x <= left + width && y >= top + height - 3 && y <= top + height + 7;
    }

    private void updateSidebar() {
        updateSelectedPeriodLabel();
        miniMonthYearLabel.setText(currentMiniMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)));
        miniCalendarGrid.getChildren().clear();
        String[] days = new String[7];
        for (int index = 0; index < 7; index++) days[index] = preferences.firstDay().plus(index)
                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault());
        for (int index = 0; index < days.length; index++) {
            Label header = new Label(days[index]);
            header.getStyleClass().addAll("calendar-day", "calendar-day-header");
            miniCalendarGrid.add(header, index, 0);
        }
        LocalDate first = currentMiniMonth.atDay(1);
        int dayOffset = Math.floorMod(first.getDayOfWeek().getValue() - preferences.firstDay().getValue(), 7);
        for (int index = 0; index < currentMiniMonth.lengthOfMonth(); index++) {
            LocalDate date = first.plusDays(index);
            Button day = new Button(String.valueOf(index + 1));
            day.getStyleClass().add("calendar-day");
            if (date.equals(datePicker.getValue())) {
                day.getStyleClass().add("calendar-day-selected");
            } else if (date.equals(LocalDate.now())) {
                day.getStyleClass().add("calendar-day-today");
            }
            day.setOnAction(event -> { datePicker.setValue(date); showCalendar.run(); });
            miniCalendarGrid.add(day, (index + dayOffset) % 7, (index + dayOffset) / 7 + 1);
        }
        resizeMiniCalendarCells();
        upcomingActivitiesList.getChildren().clear();
        boolean weekView = "Week".equals(viewMode);
        activitiesTitle.setText(weekView ? "This week's tasks" : "Today's tasks");
        List<SidebarTask> tasks = sidebarTasks(weekView);
        if (tasks.isEmpty()) {
            Label empty = new Label(weekView ? "No tasks this week." : "No tasks for this day.");
            empty.getStyleClass().add("empty-activities");
            upcomingActivitiesList.getChildren().add(empty);
            return;
        }
        for (SidebarTask sidebarTask : tasks.stream().limit(50).toList()) {
            Task task = sidebarTask.task();
            VBox item = new VBox(5);
            item.getStyleClass().add("activity-item");
            Label time = new Label(String.format("%02d:%02d - %02d:%02d", task.getStartMin() / 60,
                    task.getStartMin() % 60, (task.getStartMin() + task.getDuration()) / 60,
                    (task.getStartMin() + task.getDuration()) % 60));
            time.getStyleClass().add("activity-time");
            if (task.isAllDay()) time.setText("All day");
            else if (task.getStartMin() + task.getDuration() > 1440) time.setText(CalendarBoard.time(task.getStartMin()) + " · " + task.getDuration() / 60 + " h");
            String titleText = weekView
                    ? sidebarTask.date().format(DateTimeFormatter.ofPattern("EEEE d", Locale.ENGLISH)) + " " + task.getTitle()
                    : task.getTitle();
            Label title = new Label(titleText);
            title.getStyleClass().add("activity-title");
            item.getChildren().addAll(time, title);
            List<Note> linkedNotes = noteIntegration.notesForTask(task.getId());
            if (!linkedNotes.isEmpty()) {
                if (linkedNotes.size() == 1) {
                    Note linked = linkedNotes.getFirst();
                    String noteTitle = linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle();
                    Button openNote = new Button(noteTitle);
                    openNote.getStyleClass().add("activity-note-link");
                    openNote.setTooltip(new Tooltip("Open note: " + noteTitle));
                    openNote.setOnAction(event -> noteIntegration.openNote(linked.getId()));
                    item.getChildren().add(openNote);
                } else {
                    MenuButton note = new MenuButton("Open " + linkedNotes.size() + " notes");
                    note.getStyleClass().add("activity-note-link");
                    linkedNotes.forEach(linked -> {
                        MenuItem menuItem = new MenuItem(linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle());
                        menuItem.setOnAction(event -> noteIntegration.openNote(linked.getId()));
                        note.getItems().add(menuItem);
                    });
                    item.getChildren().add(note);
                }
            }
            item.setOnMouseClicked(event -> {
                if (isButtonTarget(event.getTarget(), item)) return;
                boolean openRequest = event.getButton() == MouseButton.SECONDARY
                        || event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 1;
                if (!openRequest) return;
                selectDate(sidebarTask.date());
                showCalendar.run();
                showTaskDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
                event.consume();
            });
            upcomingActivitiesList.getChildren().add(item);
        }
        if (tasks.size() > 50) {
            Button more = new Button("View all " + tasks.size() + " appointments"); more.getStyleClass().add("text-button");
            more.setOnAction(event -> showAgenda(weekView ? weekStart(selectedDate()) : selectedDate()));
            upcomingActivitiesList.getChildren().add(more);
        }
    }

    /** Keeps date buttons square while weekday headers expand across the seven equal columns. */
    private void resizeMiniCalendarCells() {
        double gridWidth = miniCalendarGrid.getWidth();
        if (gridWidth <= 0) return;
        double cellSize = miniCalendarCellSize(gridWidth, miniCalendarGrid.getHgap());
        miniCalendarGrid.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("calendar-day"))
                .filter(Region.class::isInstance)
                .map(Region.class::cast)
                .forEach(cell -> {
                    GridPane.setHalignment(cell, HPos.CENTER);
                    boolean weekdayHeader = cell.getStyleClass().contains("calendar-day-header");
                    if (weekdayHeader) {
                        cell.setMinSize(0, cellSize);
                        cell.setPrefSize(cellSize, cellSize);
                        cell.setMaxSize(Double.MAX_VALUE, cellSize);
                    } else {
                        cell.setMinSize(cellSize, cellSize);
                        cell.setPrefSize(cellSize, cellSize);
                        cell.setMaxSize(cellSize, cellSize);
                    }
                });
    }

    static double miniCalendarCellSize(double gridWidth, double horizontalGap) {
        if (!Double.isFinite(gridWidth) || gridWidth <= 0) return 0;
        double safeGap = Double.isFinite(horizontalGap) ? Math.max(0, horizontalGap) : 0;
        double totalGaps = safeGap * (MINI_CALENDAR_COLUMNS - 1);
        double availableCellWidth = Math.max(0, gridWidth - totalGaps) / MINI_CALENDAR_COLUMNS;
        return Math.min(MINI_CALENDAR_MAX_CELL_SIZE, availableCellWidth);
    }

    private void updateSelectedPeriodLabel() {
        if (board != null) return;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if ("Week".equals(viewMode)) {
            LocalDate start = weekStartDate;
            selectedPeriodLabel.setText("Selected week: " + formatter.format(start)
                    + " - " + formatter.format(start.plusDays(6)));
        } else {
            selectedPeriodLabel.setText("Selected day: " + formatter.format(datePicker.getValue()));
        }
    }

    private List<SidebarTask> sidebarTasks(boolean weekView) {
        LocalDate start = weekView ? weekStart(selectedDate()) : selectedDate();
        return CalendarOccurrenceService.between(tasksByDate, start, weekView ? start.plusDays(6) : start).stream()
                .map(entry -> new SidebarTask(entry.date(), entry.task())).toList();
    }

    private record SidebarTask(LocalDate date, Task task) { }

    private void selectDate(LocalDate date) {
        if (date.equals(datePicker.getValue())) refreshForSelectedDate(date);
        else datePicker.setValue(date);
    }

    private void refreshForSelectedDate(LocalDate date) {
        currentMiniMonth = YearMonth.from(date);
        if (viewMode.equals("Week")) weekStartDate = weekStart(date);
        render();
        updateSidebar();
        notifyDataChanged();
    }

    private void showTaskDialog(Task task, int start, int duration, String description) {
        editItem(datePicker.getValue(), task, start, duration, description, false);
    }

    private void notifyDataChanged() {
        if (!applyingState) dataChanged.run();
    }

    private void unlinkNotes(String taskId) {
        noteIntegration.notesForTask(taskId).forEach(note -> note.unlinkTask(taskId));
    }

    private boolean isButtonTarget(Object target, javafx.scene.Node boundary) {
        if (!(target instanceof javafx.scene.Node node)) return false;
        for (javafx.scene.Node current = node; current != null && current != boundary; current = current.getParent()) {
            if (current instanceof ButtonBase) return true;
        }
        return false;
    }

    public interface NoteIntegration {
        NoteIntegration EMPTY = new NoteIntegration() {
            @Override public List<Note> notes() { return List.of(); }
            @Override public List<NoteFolder> folders() { return List.of(); }
            @Override public List<Note> notesForTask(String taskId) { return List.of(); }
            @Override public void openNote(String noteId) { }
        };

        List<Note> notes();
        List<NoteFolder> folders();
        List<Note> notesForTask(String taskId);
        void openNote(String noteId);
    }
}
