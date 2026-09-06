package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.Task;
import com.crm.model.UserAccount;
import com.crm.service.TaskScheduleService;
import com.crm.service.CalendarOccurrenceService;
import com.crm.service.WorkspaceInsightsService;
import com.crm.model.CalendarPreferences;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.collections.FXCollections;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Renders read-only operational and analytical summaries from the existing CRM data. */
public final class OverviewController {
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final Label homeGreeting;
    private final Label homeDate;
    private final Label homeContactsCount;
    private final Label homeTodayCount;
    private final Label homeWeekCount;
    private final Label homeNextTitle;
    private final Label homeNextTime;
    private final VBox homeTodayList;
    private final VBox homeUpcomingList;
    private final VBox homeContactsList;
    private final Label dashboardContactsCount;
    private final Label dashboardActivitiesCount;
    private final Label dashboardWeekCount;
    private final Label dashboardHoursCount;
    private final PieChart dashboardTagsChart;
    private final BarChart<String, Number> dashboardActivityChart;
    private final VBox dashboardInteractionsList;

    private UserAccount user;
    private BiConsumer<LocalDate, Task> openTask = (date, task) -> { };
    private Consumer<Contact> openContact = contact -> { };
    private VBox insights;
    private Consumer<LocalDate> openDay = date -> { };
    private java.util.function.Supplier<CalendarPreferences> preferences = () -> CalendarPreferences.DEFAULT;
    public void attachInsights(VBox container, Consumer<LocalDate> openDay, java.util.function.Supplier<CalendarPreferences> preferences) {
        this.openDay = openDay; this.preferences = preferences;
        insights = new VBox(10); insights.getStyleClass().add("overview-card");
        container.getChildren().add(Math.min(2, container.getChildren().size()), insights);
        dashboardTagsChart.setMinHeight(230); dashboardTagsChart.setPrefHeight(270); dashboardTagsChart.setMaxHeight(300);
        dashboardActivityChart.setMinHeight(230); dashboardActivityChart.setPrefHeight(270); dashboardActivityChart.setMaxHeight(300);
        dashboardActivityChart.getYAxis().setLabel("Hours");
    }

    public OverviewController(Label homeGreeting, Label homeDate,
                              Label homeContactsCount, Label homeTodayCount, Label homeWeekCount,
                              Label homeNextTitle, Label homeNextTime,
                              VBox homeTodayList, VBox homeUpcomingList, VBox homeContactsList,
                              Label dashboardContactsCount, Label dashboardActivitiesCount,
                              Label dashboardWeekCount, Label dashboardHoursCount,
                              PieChart dashboardTagsChart,
                              BarChart<String, Number> dashboardActivityChart,
                              VBox dashboardInteractionsList) {
        this.homeGreeting = Objects.requireNonNull(homeGreeting);
        this.homeDate = Objects.requireNonNull(homeDate);
        this.homeContactsCount = Objects.requireNonNull(homeContactsCount);
        this.homeTodayCount = Objects.requireNonNull(homeTodayCount);
        this.homeWeekCount = Objects.requireNonNull(homeWeekCount);
        this.homeNextTitle = Objects.requireNonNull(homeNextTitle);
        this.homeNextTime = Objects.requireNonNull(homeNextTime);
        this.homeTodayList = Objects.requireNonNull(homeTodayList);
        this.homeUpcomingList = Objects.requireNonNull(homeUpcomingList);
        this.homeContactsList = Objects.requireNonNull(homeContactsList);
        this.dashboardContactsCount = Objects.requireNonNull(dashboardContactsCount);
        this.dashboardActivitiesCount = Objects.requireNonNull(dashboardActivitiesCount);
        this.dashboardWeekCount = Objects.requireNonNull(dashboardWeekCount);
        this.dashboardHoursCount = Objects.requireNonNull(dashboardHoursCount);
        this.dashboardTagsChart = Objects.requireNonNull(dashboardTagsChart);
        this.dashboardActivityChart = Objects.requireNonNull(dashboardActivityChart);
        this.dashboardInteractionsList = Objects.requireNonNull(dashboardInteractionsList);
        dashboardTagsChart.setAnimated(false);
        dashboardActivityChart.setAnimated(false);
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public void setActions(BiConsumer<LocalDate, Task> openTask, Consumer<Contact> openContact) {
        this.openTask = Objects.requireNonNull(openTask);
        this.openContact = Objects.requireNonNull(openContact);
    }

    public void refresh(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate) {
        List<Contact> safeContacts = contacts == null ? List.of() : contacts;
        Map<LocalDate, List<Task>> safeTasks = tasksByDate == null ? Map.of() : tasksByDate;
        LocalDate today = LocalDate.now();

        String firstName = user == null ? "" : firstName(user.getFullName());
        homeGreeting.setText(firstName.isBlank() ? "Welcome back" : "Welcome back, " + firstName);
        homeDate.setText(capitalize(today.format(FULL_DATE)));

        var occurrences = CalendarOccurrenceService.between(safeTasks, today, today.plusDays(6));
        List<DatedTask> todayItems = new ArrayList<>();
        Map<LocalDate, List<Task>> weekTasks = new LinkedHashMap<>();
        occurrences.forEach(entry -> {
            weekTasks.computeIfAbsent(entry.date(), ignored -> new ArrayList<>()).add(entry.task());
            if (entry.start().isBefore(today.plusDays(1).atStartOfDay()) && entry.end().isAfter(today.atStartOfDay())) todayItems.add(new DatedTask(entry.date(), entry.task()));
        });
        safeTasks.forEach((date, entries) -> entries.stream().filter(task -> !task.isScheduled() && task.getDueDate() != null).forEach(task -> {
            if (!task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(today.plusDays(6))) weekTasks.computeIfAbsent(task.getDueDate(), ignored -> new ArrayList<>()).add(task);
            if (task.getDueDate().equals(today)) todayItems.add(new DatedTask(date, task));
        }));
        List<Task> todayTasks = todayItems.stream().map(DatedTask::task).toList();
        int weekCount = weekTasks.values().stream().mapToInt(List::size).sum();
        homeContactsCount.setText(String.valueOf(safeContacts.size()));
        homeTodayCount.setText(String.valueOf(todayTasks.stream().filter(task -> !task.isCompleted()).count()));
        homeWeekCount.setText(String.valueOf(TaskScheduleService.openTasks(weekTasks).stream()
                .filter(entry -> !entry.date().isBefore(today) && !entry.date().isAfter(today.plusDays(6))).count()));
        updateNextActivity(safeTasks);
        homeTodayList.getChildren().clear();
        if (todayItems.isEmpty()) homeTodayList.getChildren().add(emptyState("No appointments or deadlines today."));
        todayItems.stream().limit(5).forEach(entry -> homeTodayList.getChildren().add(taskRow(entry.date(), entry.task(), false)));
        renderUpcoming(homeUpcomingList, weekTasks, today);
        renderContacts(homeContactsList, safeContacts);

        List<Task> allTasks = safeTasks.values().stream().flatMap(List::stream).toList();
        long totalMinutes = WorkspaceInsightsService.workload(safeTasks, today, preferences.get()).stream().mapToLong(WorkspaceInsightsService.DayLoad::minutes).sum();
        dashboardContactsCount.setText(String.valueOf(safeContacts.size()));
        dashboardActivitiesCount.setText(String.valueOf(allTasks.size()));
        dashboardWeekCount.setText(String.valueOf(weekCount));
        dashboardHoursCount.setText(formatHours(totalMinutes));
        updateTagsChart(safeContacts);
        updateActivityChart(safeTasks, today);
        renderInteractions(dashboardInteractionsList, safeContacts);
        renderInsights(safeTasks, safeContacts, today);
    }

    private void updateNextActivity(Map<LocalDate, List<Task>> tasks) {
        LocalDateTime now = LocalDateTime.now();
        TaskScheduleService.DatedTask next = TaskScheduleService.nextTask(tasks, now).orElse(null);
        if (next == null) {
            homeNextTitle.setText("Nothing scheduled");
            homeNextTime.setText("Make room for your next step");
            return;
        }
        homeNextTitle.setText(nonBlank(next.task().getTitle(), "Untitled task"));
        String day = next.date().equals(now.toLocalDate()) ? "Today"
                : next.date().equals(now.toLocalDate().plusDays(1)) ? "Tomorrow"
                : next.date().format(DateTimeFormatter.ofPattern("MMM d", ENGLISH));
        homeNextTime.setText(day + " · " + timeRange(next.task()));
    }

    private void renderTaskList(VBox target, List<Task> tasks, boolean showDate, String emptyText) {
        target.getChildren().clear();
        if (tasks.isEmpty()) {
            target.getChildren().add(emptyState(emptyText));
            return;
        }
        tasks.stream().limit(5).forEach(task -> target.getChildren().add(taskRow(null, task, showDate)));
    }

    private void renderUpcoming(VBox target, Map<LocalDate, List<Task>> tasksByDate, LocalDate today) {
        target.getChildren().clear();
        List<DatedTask> upcoming = new ArrayList<>();
        tasksByDate.forEach((date, tasks) -> {
            if (date.isAfter(today) && !date.isAfter(today.plusDays(6))) {
                tasks.stream().filter(task -> !task.isCompleted())
                        .forEach(task -> upcoming.add(new DatedTask(date, task)));
            }
        });
        upcoming.sort(Comparator.comparing(DatedTask::date).thenComparing(entry -> entry.task().getStartMin()));
        if (upcoming.isEmpty()) {
            target.getChildren().add(emptyState("No tasks in the next 7 days."));
            return;
        }
        upcoming.stream().limit(5).forEach(entry ->
                target.getChildren().add(taskRow(entry.date(), entry.task(), true)));
    }

    private HBox taskRow(LocalDate date, Task task, boolean showDate) {
        Label marker = new Label();
        marker.getStyleClass().addAll("overview-marker", "marker-" + safeColor(task.getColor()));
        VBox text = new VBox(3);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label(nonBlank(task.getTitle(), "Untitled task"));
        title.getStyleClass().add("overview-item-title");
        String detail = showDate && date != null
                ? capitalize(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ENGLISH)) + " " + date.getDayOfMonth()
                    + " · " + timeRange(task)
                : timeRange(task);
        Label subtitle = new Label(detail);
        subtitle.getStyleClass().add("overview-item-subtitle");
        text.getChildren().addAll(title, subtitle);
        HBox row = new HBox(11, marker, text);
        row.getStyleClass().add("overview-list-row");
        if (task.isCompleted()) row.getStyleClass().add("overview-row-completed");
        makeActionable(row, "Open task: " + title.getText(),
                () -> openTask.accept(date == null ? LocalDate.now() : date, task));
        return row;
    }

    private void renderContacts(VBox target, List<Contact> contacts) {
        target.getChildren().clear();
        List<Contact> sorted = WorkspaceInsightsService.reconnect(contacts, LocalDate.now()).stream()
                .limit(5)
                .toList();
        if (sorted.isEmpty()) {
            target.getChildren().add(emptyState(contacts.isEmpty() ? "Your address book is still empty." : "Your contacts have recent dated interactions."));
            return;
        }
        sorted.forEach(contact -> target.getChildren().add(contactRow(contact, true)));
    }

    private void renderInteractions(VBox target, List<Contact> contacts) {
        target.getChildren().clear();
        record InteractionRow(Contact contact, com.crm.model.ContactInteraction interaction) { }
        var interactions = contacts.stream().flatMap(contact -> contact.getInteractions().stream().map(item -> new InteractionRow(contact, item)))
                .sorted(Comparator.comparing((InteractionRow item) -> item.interaction().date()).reversed()).limit(6).toList();
        if (interactions.isEmpty()) {
            target.getChildren().add(emptyState("No interactions recorded."));
            return;
        }
        interactions.forEach(item -> {
            Label heading = new Label(item.contact().nameProperty().get() + " · " + item.interaction().kind()); heading.getStyleClass().add("overview-item-title");
            Label detail = new Label(item.interaction().date() + " · " + item.interaction().summary()); detail.setWrapText(true); detail.getStyleClass().add("overview-item-subtitle");
            HBox row = new HBox(new VBox(4, heading, detail)); row.getStyleClass().add("overview-list-row");
            makeActionable(row, "Open contact " + item.contact().nameProperty().get(), () -> openContact.accept(item.contact())); target.getChildren().add(row);
        });
    }

    private HBox contactRow(Contact contact, boolean showInteraction) {
        String name = nonBlank(contact.nameProperty().get(), "Unnamed contact");
        Label initial = new Label(name.substring(0, 1).toUpperCase(ENGLISH));
        initial.getStyleClass().add("contact-initial");
        VBox text = new VBox(3);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label(name);
        title.getStyleClass().add("overview-item-title");
        String secondary = showInteraction
                ? WorkspaceInsightsService.lastContact(contact).map(date -> "Last contact: " + date).orElse("No dated interaction yet")
                : nonBlank(contact.companyProperty().get(), nonBlank(contact.emailProperty().get(), "No details"));
        Label subtitle = new Label(secondary);
        subtitle.getStyleClass().add("overview-item-subtitle");
        text.getChildren().addAll(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tag = new Label(nonBlank(contact.tagsProperty().get(), "No tag"));
        tag.getStyleClass().add("overview-tag");
        HBox row = new HBox(11, initial, text, spacer, tag);
        row.getStyleClass().add("overview-list-row");
        makeActionable(row, "Open contact: " + name, () -> openContact.accept(contact));
        return row;
    }

    private static void makeActionable(HBox row, String description, Runnable action) {
        row.setFocusTraversable(true);
        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.setAccessibleText(description);
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) action.run();
        });
        row.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                action.run();
                event.consume();
            }
        });
    }

    private void updateTagsChart(List<Contact> contacts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        contacts.forEach(contact -> {
            String tag = nonBlank(contact.tagsProperty().get(), "No tag");
            counts.merge(tag, 1, Integer::sum);
        });
        dashboardTagsChart.setData(FXCollections.observableArrayList(
                counts.entrySet().stream()
                        .map(entry -> new PieChart.Data(entry.getKey(), entry.getValue()))
                        .toList()));
    }

    private void updateActivityChart(Map<LocalDate, List<Task>> tasksByDate, LocalDate today) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Planned hours");
        var workload = WorkspaceInsightsService.workload(tasksByDate, today, preferences.get());
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = today.plusDays(offset);
            String day = capitalize(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ENGLISH));
            series.getData().add(new XYChart.Data<>(day, workload.get(offset).minutes() / 60.0));
        }
        dashboardActivityChart.getData().setAll(series);
    }

    static int countBetween(Map<LocalDate, List<Task>> tasks, LocalDate from, LocalDate to) {
        return tasks.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
                .mapToInt(entry -> entry.getValue().size())
                .sum();
    }

    private static List<Task> sortedTasks(List<Task> tasks) {
        return tasks.stream().sorted(Comparator.comparingInt(Task::getStartMin)).toList();
    }

    private static Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("overview-empty");
        label.setWrapText(true);
        return label;
    }

    private static String timeRange(Task task) {
        if (!task.isScheduled()) return task.getDueDate() == null ? "No deadline" : "Due " + task.getDueDate();
        if (task.isAllDay()) return "All day";
        if (task.getStartMin() + task.getDuration() > 1440) return "Multi-day · " + formatHours(task.getDuration());
        int end = task.getStartMin() + task.getDuration();
        return String.format("%02d:%02d – %02d:%02d",
                task.getStartMin() / 60, task.getStartMin() % 60, end / 60, end % 60);
    }

    static String formatHours(long minutes) {
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return remainder == 0 ? hours + " h" : hours + " h " + remainder + " m";
    }
    private void renderInsights(Map<LocalDate, List<Task>> tasks, List<Contact> contacts, LocalDate today) {
        if (insights == null) return;
        insights.getChildren().clear(); Label heading = new Label("Needs attention"); heading.getStyleClass().add("overview-card-title"); insights.getChildren().add(heading);
        var listed = TaskScheduleService.listedTasks(tasks, today);
        long unplanned = listed.stream().filter(entry -> !entry.task().isScheduled() && !entry.task().isCompleted()).count();
        long priority = listed.stream().filter(entry -> !entry.task().isCompleted() && entry.task().getPriority().ordinal() >= Task.Priority.HIGH.ordinal()).count();
        Label summary = new Label(TaskScheduleService.overdueCount(tasks, LocalDateTime.now()) + " overdue · " + priority + " high priority · " + unplanned + " to plan · "
                + WorkspaceInsightsService.reconnect(contacts, today).size() + " contacts to reconnect"); summary.setWrapText(true); summary.getStyleClass().add("section-subtitle"); insights.getChildren().add(summary);
        listed.stream().filter(entry -> !entry.task().isCompleted() && (TaskScheduleService.isOverdue(entry.date(), entry.task(), LocalDateTime.now())
                        || entry.task().getPriority().ordinal() >= Task.Priority.HIGH.ordinal()))
                .sorted(Comparator.comparingInt((TaskScheduleService.DatedTask entry) -> entry.task().getPriority().ordinal()).reversed())
                .limit(3).forEach(entry -> insights.getChildren().add(taskRow(entry.date(), entry.task(), true)));
        WorkspaceInsightsService.workload(tasks, today, preferences.get()).stream().filter(WorkspaceInsightsService.DayLoad::overloaded).forEach(load -> {
            javafx.scene.control.Button button = new javafx.scene.control.Button(load.date().format(DateTimeFormatter.ofPattern("EEE d MMM")) + ": " + formatHours(load.minutes()) + " planned — review this day");
            button.getStyleClass().add("text-button"); button.setWrapText(true); button.setOnAction(event -> openDay.accept(load.date())); insights.getChildren().add(button);
        });
    }

    private static String firstName(String fullName) {
        String value = value(fullName).trim();
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static String safeColor(String color) {
        String value = value(color).toLowerCase(ENGLISH);
        return List.of("blue", "red", "green", "yellow", "orange", "purple").contains(value)
                ? value : "blue";
    }

    private static String nonBlank(String value, String fallback) {
        String normalized = value(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String capitalize(String value) {
        return value == null || value.isBlank() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record DatedTask(LocalDate date, Task task) {}
}
