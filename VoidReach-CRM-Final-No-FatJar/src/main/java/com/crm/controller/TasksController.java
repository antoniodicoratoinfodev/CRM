package com.crm.controller;

import com.crm.model.Task;
import com.crm.model.Note;
import com.crm.service.ThemeService;
import com.crm.service.TaskScheduleService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Presents and controls the same activities stored by the calendar. */
public final class TasksController {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);
    private static final String ALL = "All tasks";
    private static final String TODAY = "Today";
    private static final String UPCOMING = "Upcoming";
    private static final String OVERDUE = "Overdue";
    private static final String COMPLETED = "Completed";
    private static final double TIME_COLUMN_WIDTH = 118.0;
    private static final double STATUS_COLUMN_WIDTH = 76.0;

    private final TextField searchField;
    private final ComboBox<String> filter;
    private final VBox list;
    private final Label emptyLabel;
    private final Label totalCount;
    private final Label todayCount;
    private final Label upcomingCount;
    private final Label completedCount;
    private final ThemeService themeService;
    private final TaskActions actions;
    private final List<DatedTask> tasks = new ArrayList<>();
    private int page;
    private static final int PAGE_SIZE = 100;

    public TasksController(TextField searchField, ComboBox<String> filter, VBox list, Label emptyLabel,
                           Label totalCount, Label todayCount, Label upcomingCount, Label completedCount,
                           ThemeService themeService, TaskActions actions) {
        this.searchField = Objects.requireNonNull(searchField);
        this.filter = Objects.requireNonNull(filter);
        this.list = Objects.requireNonNull(list);
        this.emptyLabel = Objects.requireNonNull(emptyLabel);
        this.totalCount = Objects.requireNonNull(totalCount);
        this.todayCount = Objects.requireNonNull(todayCount);
        this.upcomingCount = Objects.requireNonNull(upcomingCount);
        this.completedCount = Objects.requireNonNull(completedCount);
        this.themeService = Objects.requireNonNull(themeService);
        this.actions = Objects.requireNonNull(actions);
    }

    public void initialize() {
        filter.setItems(FXCollections.observableArrayList(ALL, TODAY, UPCOMING, OVERDUE, "Unscheduled", "High priority", "In progress", COMPLETED));
        filter.setValue(ALL);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> { page = 0; render(); });
        filter.valueProperty().addListener((observable, oldValue, newValue) -> { page = 0; render(); });
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        render();
    }

    public void refresh(Map<LocalDate, List<Task>> tasksByDate) {
        tasks.clear();
        if (tasksByDate != null) TaskScheduleService.listedTasks(tasksByDate, LocalDate.now()).forEach(entry -> tasks.add(new DatedTask(entry.date(), entry.task())));
        tasks.sort(Comparator.comparing(this::displayDate)
                .thenComparingInt(entry -> entry.task().getStartMin()));
        updateMetrics();
        render();
    }

    public void showAttention(boolean hasOverdueTasks) {
        searchField.clear();
        filter.setValue(hasOverdueTasks ? OVERDUE : TODAY);
        render();
    }

    public void refreshClock() {
        updateMetrics();
        render();
    }

    private void updateMetrics() {
        LocalDate today = LocalDate.now();
        totalCount.setText(String.valueOf(tasks.size()));
        todayCount.setText(String.valueOf(tasks.stream()
                .filter(entry -> displayDate(entry).equals(today) && !entry.task().isCompleted()).count()));
        upcomingCount.setText(String.valueOf(tasks.stream()
                .filter(entry -> !entry.task().isCompleted()
                        && !displayDate(entry).isBefore(today) && !displayDate(entry).isAfter(today.plusDays(6))).count()));
        completedCount.setText(String.valueOf(tasks.stream().filter(entry -> entry.task().isCompleted()).count()));
    }

    private void render() {
        list.getChildren().clear();
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        String selectedFilter = filter.getValue() == null ? ALL : filter.getValue();
        List<DatedTask> matchingTasks = tasks.stream()
                .filter(entry -> matchesFilter(entry, selectedFilter))
                .filter(entry -> query.isEmpty()
                        || safe(entry.task().getTitle()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(entry.task().getDescription()).toLowerCase(Locale.ROOT).contains(query))
                .toList();
        page = Math.max(0, Math.min(page, (matchingTasks.size() - 1) / PAGE_SIZE));
        List<DatedTask> visibleTasks = matchingTasks.stream().skip((long)page * PAGE_SIZE).limit(PAGE_SIZE).toList();
        Map<LocalDate, Long> dayCounts = matchingTasks.stream().collect(java.util.stream.Collectors.groupingBy(this::displayDate, java.util.stream.Collectors.counting()));

        LocalDate currentDate = null;
        for (DatedTask entry : visibleTasks) {
            if (!displayDate(entry).equals(currentDate)) {
                currentDate = displayDate(entry);
                LocalDate headerDate = currentDate;
                long dayCount = dayCounts.getOrDefault(headerDate, 0L);
                list.getChildren().add(dateHeader(headerDate, dayCount));
            }
            list.getChildren().add(taskRow(entry));
        }
        emptyLabel.setText(tasks.isEmpty()
                ? "There are no tasks yet. Create one to get started."
                : "No tasks match the selected filters.");
        emptyLabel.setVisible(visibleTasks.isEmpty());
        if (matchingTasks.size() > PAGE_SIZE) {
            Button previous = new Button("Previous"); previous.setDisable(page == 0); previous.setOnAction(e -> { page--; render(); });
            Button next = new Button("Next"); next.setDisable((page + 1) * PAGE_SIZE >= matchingTasks.size()); next.setOnAction(e -> { page++; render(); });
            list.getChildren().add(new HBox(12, previous, new Label((page * PAGE_SIZE + 1) + "–" + Math.min((page + 1) * PAGE_SIZE, matchingTasks.size()) + " of " + matchingTasks.size()), next));
        }
    }

    private boolean matchesFilter(DatedTask entry, String selectedFilter) {
        LocalDate today = LocalDate.now();
        return switch (selectedFilter) {
            case TODAY -> displayDate(entry).equals(today);
            case UPCOMING -> !entry.task().isCompleted() && !displayDate(entry).equals(LocalDate.MAX) && !displayDate(entry).isBefore(today);
            case OVERDUE -> isOverdue(entry);
            case COMPLETED -> entry.task().isCompleted();
            case "Unscheduled" -> !entry.task().isScheduled() && !entry.task().isCompleted();
            case "High priority" -> entry.task().getPriority().ordinal() >= Task.Priority.HIGH.ordinal() && !entry.task().isCompleted();
            case "In progress" -> entry.task().getStatus() == Task.Status.IN_PROGRESS;
            default -> true;
        };
    }

    private HBox taskRow(DatedTask entry) {
        Task task = entry.task();
        HBox row = new HBox(13);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("task-list-row");
        if (task.isCompleted()) row.getStyleClass().add("task-list-row-completed");

        CheckBox completed = new CheckBox();
        completed.setSelected(task.isCompleted());
        completed.setAccessibleText("Mark as completed");
        completed.setOnAction(event -> actions.setCompleted(entry.date(), task, completed.isSelected()));

        Region marker = new Region();
        marker.getStyleClass().addAll("task-list-marker", "marker-" + safeColor(task.getColor()));

        Label title = new Label(displayTitle(task));
        title.setMaxWidth(Double.MAX_VALUE);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.getStyleClass().add("task-list-title");
        VBox details = new VBox(4, title);
        if (task.getPriority() != Task.Priority.NORMAL) {
            Label priority = new Label(task.getPriority() + " priority"); priority.getStyleClass().add("task-list-description"); details.getChildren().add(priority);
        }
        details.setAlignment(Pos.CENTER_LEFT);
        details.setMinWidth(0);
        HBox.setHgrow(details, Priority.ALWAYS);
        if (!safe(task.getDescription()).isBlank()) {
            Label description = new Label(task.getDescription());
            description.setMaxWidth(Double.MAX_VALUE);
            description.setTextOverrun(OverrunStyle.ELLIPSIS);
            description.getStyleClass().add("task-list-description");
            details.getChildren().add(description);
        }

        List<Note> linkedNotes = actions.linkedNotes(task.getId());
        if (!linkedNotes.isEmpty()) {
            FlowPane noteLinks = new FlowPane(4, 4);
            noteLinks.setPrefWrapLength(240);
            noteLinks.setMaxWidth(Double.MAX_VALUE);
            noteLinks.getStyleClass().add("task-note-links");
            Label noteCaption = new Label("Notes");
            noteCaption.getStyleClass().add("task-note-caption");
            noteLinks.getChildren().add(noteCaption);
            linkedNotes.forEach(linked -> {
                String noteTitle = safe(linked.getTitle()).isBlank() ? "Untitled note" : linked.getTitle();
                Button note = new Button(noteTitle);
                note.getStyleClass().add("task-note-link");
                note.setMaxWidth(160);
                note.setTextOverrun(OverrunStyle.ELLIPSIS);
                note.setTooltip(new Tooltip("Open note: " + noteTitle));
                note.setOnAction(event -> actions.openNote(linked.getId()));
                noteLinks.getChildren().add(note);
            });
            details.getChildren().add(noteLinks);
        }

        FontIcon clockIcon = new FontIcon("fas-clock");
        clockIcon.getStyleClass().add("task-time-icon");
        Label time = new Label(timeRange(task));
        time.getStyleClass().add("task-list-time");
        HBox timeChip = new HBox(5, clockIcon, time);
        timeChip.setAlignment(Pos.CENTER);
        timeChip.getStyleClass().add("task-time-chip");
        HBox schedule = new HBox(timeChip);
        schedule.setAlignment(Pos.CENTER_LEFT);
        schedule.setMinWidth(TIME_COLUMN_WIDTH);
        schedule.setPrefWidth(TIME_COLUMN_WIDTH);
        schedule.setMaxWidth(TIME_COLUMN_WIDTH);

        Label status = new Label(status(entry));
        status.getStyleClass().addAll("task-status", statusClass(entry));
        HBox statusColumn = new HBox(status);
        statusColumn.setAlignment(Pos.CENTER);
        statusColumn.setMinWidth(STATUS_COLUMN_WIDTH);
        statusColumn.setPrefWidth(STATUS_COLUMN_WIDTH);
        statusColumn.setMaxWidth(STATUS_COLUMN_WIDTH);

        Button calendar = iconButton("fas-calendar-day", "Show in calendar");
        calendar.setOnAction(event -> actions.openCalendar(entry.date()));
        Button edit = iconButton("fas-pen", "Edit task");
        edit.setOnAction(event -> actions.edit(entry.date(), task));
        Button delete = iconButton("fas-trash-alt", "Delete task");
        delete.getStyleClass().add("task-delete-button");
        delete.setOnAction(event -> confirmDelete(entry));
        HBox controls = new HBox(4, calendar, edit, delete);
        controls.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(completed, marker, details, schedule, statusColumn, controls);
        row.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || isInteractiveChild(event.getTarget(), row)) return;
            actions.edit(entry.date(), task);
        });
        return row;
    }

    private HBox dateHeader(LocalDate date, long taskCount) {
        LocalDate today = LocalDate.now();
        Label title = new Label(headerTitle(date, today).toUpperCase(Locale.ENGLISH));
        title.getStyleClass().add("task-date-header-title");
        HBox header = new HBox(8, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("task-date-header");
        if (isNearToday(date, today)) {
            Label fullDate = new Label(date.format(DATE_FORMAT));
            fullDate.getStyleClass().add("task-date-header-date");
            header.getChildren().add(fullDate);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label count = new Label(taskCount == 1 ? "1 task" : taskCount + " tasks");
        count.getStyleClass().add("task-date-header-count");
        header.getChildren().addAll(spacer, count);
        if (date.isBefore(today)) header.getStyleClass().add("task-date-header-past");
        return header;
    }

    private String headerTitle(LocalDate date, LocalDate today) {
        if (date.equals(LocalDate.MAX)) return "Backlog · no deadline";
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        return date.format(DATE_FORMAT);
    }

    private boolean isNearToday(LocalDate date, LocalDate today) {
        return !date.isBefore(today.minusDays(1)) && !date.isAfter(today.plusDays(1));
    }

    private Button iconButton(String iconLiteral, String description) {
        Button button = new Button();
        button.setGraphic(new FontIcon(iconLiteral));
        button.getStyleClass().add("task-icon-button");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private boolean isInteractiveChild(Object target, HBox row) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null && current != row; current = current.getParent()) {
            if (current instanceof ButtonBase) return true;
        }
        return false;
    }

    private void confirmDelete(DatedTask entry) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete task");
        confirmation.setHeaderText("Delete “" + displayTitle(entry.task()) + "”?");
        confirmation.setContentText("The task will also be removed from the calendar.");
        themeService.applyTo(confirmation);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            actions.delete(entry.date(), entry.task());
        }
    }

    private boolean isOverdue(DatedTask entry) {
        return TaskScheduleService.isOverdue(entry.date(), entry.task(), LocalDateTime.now());
    }

    private String status(DatedTask entry) {
        if (entry.task().isCompleted()) return "Completed";
        if (isOverdue(entry)) return "Overdue";
        if (entry.task().getStatus() == Task.Status.IN_PROGRESS) return "In progress";
        if (!entry.task().isScheduled()) return "To do";
        if (entry.date().equals(LocalDate.now())) return "Today";
        return "Scheduled";
    }

    private String statusClass(DatedTask entry) {
        if (entry.task().isCompleted()) return "task-status-completed";
        if (isOverdue(entry)) return "task-status-overdue";
        if (entry.date().equals(LocalDate.now())) return "task-status-today";
        return "task-status-planned";
    }

    private String timeRange(Task task) {
        if (!task.isScheduled()) return task.getDueDate() == null ? "No deadline" : "Due " + task.getDueDate().format(DateTimeFormatter.ofPattern("d MMM"));
        if (task.isAllDay()) return "All day";
        if (task.getStartMin() + task.getDuration() > 1440) return task.getDuration() / 60 + " h · multi-day";
        int end = task.getStartMin() + task.getDuration();
        return String.format("%02d:%02d – %02d:%02d", task.getStartMin() / 60,
                task.getStartMin() % 60, end / 60, end % 60);
    }

    private String safeColor(String color) {
        String normalized = safe(color).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "red", "green", "yellow", "orange", "purple" -> normalized;
            default -> "blue";
        };
    }

    private String displayTitle(Task task) {
        return safe(task.getTitle()).isBlank() ? "Untitled task" : task.getTitle();
    }

    private String safe(String value) { return value == null ? "" : value; }
    private LocalDate displayDate(DatedTask entry) {
        return entry.task().isScheduled() ? entry.date() : Objects.requireNonNullElse(entry.task().getDueDate(), LocalDate.MAX);
    }

    private record DatedTask(LocalDate date, Task task) { }

    public interface TaskActions {
        void edit(LocalDate date, Task task);
        void delete(LocalDate date, Task task);
        void setCompleted(LocalDate date, Task task, boolean completed);
        void openCalendar(LocalDate date);
        List<Note> linkedNotes(String taskId);
        void openNote(String noteId);
    }
}
