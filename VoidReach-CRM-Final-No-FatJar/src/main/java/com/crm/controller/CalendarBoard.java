package com.crm.controller;

import com.crm.model.Task;
import com.crm.service.CalendarLayoutService;
import com.crm.service.CalendarOccurrenceService;
import com.crm.service.CalendarOccurrenceService.Occurrence;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Calendar presentation and transient gestures; domain changes are committed by the controller. */
final class CalendarBoard {
    private final CalendarController owner;
    private final VBox view;
    private final AnchorPane timeline, times;
    private final HBox row, headers = new HBox(), allDay = new HBox();
    private final ScrollPane scroll, alternate = new ScrollPane();
    private final VBox alternateContent = new VBox(10);
    private final Label period;
    private final HBox zoomControls;
    private final MenuButton snapMenu = new MenuButton();
    private LocalDate lastDate;
    private String lastMode;
    private Runnable cancelGesture = () -> { };
    private boolean gesture;
    private double width, dayWidth, minuteHeight;
    private LocalDate first;
    private int dayCount;
    private double pointerY;
    private Runnable dragUpdate = () -> { };
    private final javafx.animation.AnimationTimer edgeScroll = new javafx.animation.AnimationTimer() {
        private long previous;
        @Override public void handle(long now) {
            if (!gesture || !view.isVisible() || view.getScene() == null) { stop(); previous = 0; return; }
            double elapsed = previous == 0 ? 0 : Math.min(0.05, (now - previous) / 1_000_000_000.0); previous = now;
            var bounds = scroll.localToScene(scroll.getBoundsInLocal());
            double direction = pointerY < bounds.getMinY() + 36 ? -1 : pointerY > bounds.getMaxY() - 36 ? 1 : 0;
            double range = Math.max(1, row.getHeight() - scroll.getViewportBounds().getHeight());
            double value = Math.max(0, Math.min(1, scroll.getVvalue() + direction * elapsed * 420 / range));
            if (value != scroll.getVvalue()) { scroll.setVvalue(value); scroll.layout(); dragUpdate.run(); }
        }
    };

    private void followPointer(double sceneY, Runnable update) {
        pointerY = sceneY; dragUpdate = update; update.run(); edgeScroll.start();
    }
    private void endGesture() { gesture = false; edgeScroll.stop(); dragUpdate = () -> { }; }

    CalendarBoard(CalendarController owner, VBox view, AnchorPane timeline, AnchorPane times,
                  HBox row, ScrollPane scroll, DatePicker date, ComboBox<String> mode, Label period, Label zoom) {
        this.owner = owner; this.view = view; this.timeline = timeline; this.times = times;
        this.row = row; this.scroll = scroll; this.period = period;
        Label eyebrow = label("YOUR TIME, IN FOCUS", "page-eyebrow");
        period.getStyleClass().setAll("calendar-period-title");
        VBox heading = new VBox(4, eyebrow, period);
        Button create = button("+ New", "btn-primary", () -> owner.createEvent(owner.selectedDate(), 9 * 60, 60));
        HBox top = new HBox(12, heading, space(), create); top.setAlignment(Pos.CENTER_LEFT);
        date.setPrefWidth(142); mode.setPrefWidth(110);
        zoomControls = new HBox(3, button("−", "icon-button", owner::zoomOut), zoom,
                button("+", "icon-button", owner::zoomIn)); zoomControls.setAlignment(Pos.CENTER);
        MenuButton more = new MenuButton("•••"); more.getStyleClass().add("calendar-more");
        MenuItem preferences = new MenuItem("Calendar preferences…"); preferences.setOnAction(e -> owner.showPreferences());
        MenuItem importIcs = new MenuItem("Import calendar (.ics)…"); importIcs.setOnAction(e -> owner.importCalendar());
        MenuItem exportIcs = new MenuItem("Export calendar (.ics)…"); exportIcs.setOnAction(e -> owner.exportCalendar());
        more.getItems().setAll(preferences, importIcs, exportIcs);
        snapMenu.setId("calendarSnapMenu"); snapMenu.getStyleClass().add("btn-secondary");
        snapMenu.setTooltip(new Tooltip("Snap dragged/resized events to the selected interval. Grid follows the 15-minute lines. Hold Alt for 1 minute."));
        ToggleGroup snapChoices = new ToggleGroup();
        for (com.crm.model.CalendarPreferences.Snap option : com.crm.model.CalendarPreferences.Snap.values()) {
            RadioMenuItem item = new RadioMenuItem(option.toString()); item.setUserData(option); item.setToggleGroup(snapChoices);
            item.setId("calendarSnap" + option.minutes()); item.setOnAction(e -> owner.setSnap(option)); snapMenu.getItems().add(item);
        }
        FlowPane toolbar = new FlowPane(8, 8, button("‹", "icon-button", owner::previousPeriod),
                button("Today", "btn-secondary", owner::today), button("›", "icon-button", owner::nextPeriod),
                date, mode, snapMenu, zoomControls, more);
        toolbar.getStyleClass().add("calendar-toolbar-v2");
        headers.getStyleClass().add("calendar-fixed-headers");
        allDay.getStyleClass().add("calendar-all-day-row");
        alternate.setFitToWidth(true); alternate.setContent(alternateContent); alternate.getStyleClass().add("calendar-alternate");
        StackPane body = new StackPane(scroll, alternate); body.getStyleClass().add("calendar-board-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        view.getChildren().setAll(top, toolbar, headers, allDay, body);
        view.setSpacing(10);
        scroll.setContent(row); scroll.setFitToWidth(true);
        row.getChildren().setAll(times, timeline);
        timeline.setFocusTraversable(true);
        view.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && gesture) { cancelGesture.run(); event.consume(); }
            if (event.getTarget() instanceof TextInputControl || event.getTarget() instanceof ComboBoxBase<?>) return;
            if (event.getCode() == KeyCode.T && !event.isShortcutDown()) { owner.today(); event.consume(); }
            if (event.getCode() == KeyCode.N && !event.isShortcutDown()) { create.fire(); event.consume(); }
        });
    }

    void render() {
        if (gesture) return;
        boolean timed = List.of("Day", "Week").contains(owner.viewMode());
        snapMenu.setText("Snap: " + owner.preferences().snapMode()); visible(snapMenu, timed);
        for (MenuItem item : snapMenu.getItems()) ((RadioMenuItem)item).setSelected(item.getUserData() == owner.preferences().snapMode());
        visible(scroll, timed); visible(alternate, !timed); visible(headers, timed); visible(allDay, timed);
        visible(zoomControls, timed);
        period.setText(switch (owner.viewMode()) {
            case "Week" -> owner.weekStart(owner.selectedDate()).format(DateTimeFormatter.ofPattern("d MMM")) + " – "
                    + owner.weekStart(owner.selectedDate()).plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"));
            case "Month" -> owner.selectedDate().format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            case "Agenda" -> "Agenda · " + owner.selectedDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
            default -> owner.selectedDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"));
        });
        if (!timed) { if (owner.viewMode().equals("Month")) renderMonth(); else renderAgenda(); return; }
        dayCount = owner.viewMode().equals("Week") ? 7 : 1;
        first = dayCount == 7 ? owner.weekStart(owner.selectedDate()) : owner.selectedDate();
        width = Math.max(320, scroll.getViewportBounds().getWidth() - 64);
        dayWidth = width / dayCount; minuteHeight = owner.zoom();
        fixed(times, 64, 1440 * minuteHeight); fixed(timeline, width, 1440 * minuteHeight);
        row.setMinHeight(1440 * minuteHeight); row.setPrefHeight(1440 * minuteHeight); row.setMaxHeight(1440 * minuteHeight);
        timeline.getChildren().clear(); times.getChildren().clear(); headers.getChildren().clear(); allDay.getChildren().clear();
        Region corner = new Region(); fixed(corner, 64, 44); headers.getChildren().add(corner);
        Label allDayCaption = label("All day", "calendar-all-day-caption"); fixed(allDayCaption, 64, 32); allDay.getChildren().add(allDayCaption);
        List<Occurrence> occurrences = CalendarOccurrenceService.between(owner.tasksSnapshot(), first, first.plusDays(dayCount - 1));
        for (int day = 0; day < dayCount; day++) {
            LocalDate date = first.plusDays(day);
            Button header = button(date.format(DateTimeFormatter.ofPattern("EEE d")), "calendar-day-heading", () -> owner.selectDay(date));
            fixed(header, dayWidth, 44);
            if (date.equals(LocalDate.now())) header.getStyleClass().add("calendar-heading-today");
            headers.getChildren().add(header);
            Region work = new Region(); work.getStyleClass().add("calendar-working-hours");
            put(work, day * dayWidth, owner.preferences().workStart() * minuteHeight,
                    dayWidth, (owner.preferences().workEnd() - owner.preferences().workStart()) * minuteHeight);
            VBox dayAll = new VBox(3); dayAll.setMinWidth(0); dayAll.setPrefWidth(dayWidth); dayAll.setMaxWidth(dayWidth);
            List<Occurrence> entries = occurrences.stream().filter(o -> intersects(o, date)).toList();
            List<Occurrence> fullDays = entries.stream().filter(o -> o.task().isAllDay()).toList();
            fullDays.stream().limit(3).forEach(o -> dayAll.getChildren().add(eventButton(o, "calendar-all-day-event")));
            if (fullDays.size() > 3) dayAll.getChildren().add(button("+" + (fullDays.size() - 3) + " more", "text-button", () -> owner.showAgenda(date)));
            allDay.getChildren().add(dayAll);
            if (day > 0) { Region divider = new Region(); divider.getStyleClass().add("calendar-day-divider"); put(divider, day * dayWidth, 0, 1, 1440 * minuteHeight); }
        }
        for (int minute = 0; minute <= 1440; minute += com.crm.model.CalendarPreferences.GRID_MINUTES) {
            Region line = new Region(); line.getStyleClass().add(minute % 60 == 0 ? "calendar-hour-line" : "calendar-minute-line");
            put(line, 0, minute * minuteHeight, width, 1);
            if (minute % 60 == 0 && minute < 1440) {
                Label time = label(time(minute), "calendar-time-label");
                AnchorPane.setTopAnchor(time, Math.max(0, minute * minuteHeight - 8)); AnchorPane.setRightAnchor(time, 10.0);
                times.getChildren().add(time);
            }
        }
        for (int day = 0; day < dayCount; day++) {
            LocalDate date = first.plusDays(day);
            List<Occurrence> entries = occurrences.stream().filter(o -> !o.task().isAllDay() && intersects(o, date)).toList();
            if (entries.size() > 200) {
                Button dense = button(entries.size() + " appointments\nOpen Agenda to browse all", "calendar-dense-link", () -> owner.showAgenda(date));
                dense.setWrapText(true); dense.setAccessibleText(entries.size() + " appointments on " + date + ". Open Agenda.");
                put(dense, day * dayWidth + 4, owner.preferences().workStart() * minuteHeight + 10, dayWidth - 8, 110);
                dense.setMouseTransparent(false);
                continue;
            }
            Map<String, CalendarLayoutService.Placement> layout = CalendarLayoutService.arrange(entries.stream()
                    .map(o -> new CalendarLayoutService.Interval(o.key(), o.startMinuteOn(date), o.endMinuteOn(date))).toList());
            for (Occurrence entry : entries) renderEntry(entry, date, day, layout.get(entry.key()));
        }
        if (!LocalDate.now().isBefore(first) && LocalDate.now().isBefore(first.plusDays(dayCount))) {
            int day = (int)java.time.temporal.ChronoUnit.DAYS.between(first, LocalDate.now());
            Region now = new Region(); now.getStyleClass().add("calendar-now-line");
            put(now, day * dayWidth, (LocalTime.now().getHour() * 60 + LocalTime.now().getMinute()) * minuteHeight, dayWidth, 2);
        }
        installRangeCreation();
        if (!Objects.equals(lastDate, owner.selectedDate()) || !Objects.equals(lastMode, owner.viewMode())) {
            lastDate = owner.selectedDate(); lastMode = owner.viewMode();
            Platform.runLater(() -> {
                double height = 1440 * minuteHeight - scroll.getViewportBounds().getHeight();
                scroll.setVvalue(height <= 0 ? 0 : Math.min(1, owner.preferences().workStart() * minuteHeight / height));
            });
        }
    }

    private void renderEntry(Occurrence entry, LocalDate day, int dayIndex, CalendarLayoutService.Placement placement) {
        int start = entry.startMinuteOn(day), end = entry.endMinuteOn(day);
        double columnWidth = dayWidth / placement.columns();
        VBox box = new VBox(3); box.getStyleClass().addAll("task-entry", "calendar-event-v2", "task-" + safeColor(entry.task()));
        if (entry.task().isCompleted()) box.getStyleClass().add("task-entry-completed");
        Label title = label(entry.task().getTitle(), "task-title"); title.setWrapText(true);
        Label clock = label(time(start) + " – " + time(end), "task-time");
        box.getChildren().setAll(title, clock);
        if (end - start < 35) visible(clock, false);
        double left = dayIndex * dayWidth + placement.column() * columnWidth + 3, top = start * minuteHeight;
        fixed(box, Math.max(2, columnWidth - 6), (end - start) * minuteHeight);
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(box.widthProperty()); clip.heightProperty().bind(box.heightProperty());
        clip.setArcWidth(8); clip.setArcHeight(8); box.setClip(clip);
        AnchorPane.setLeftAnchor(box, left); AnchorPane.setTopAnchor(box, top); timeline.getChildren().add(box);
        box.setFocusTraversable(true); box.setAccessibleText(entry.task().getTitle() + ", " + day + ", " + clock.getText());
        Tooltip.install(box, new Tooltip(entry.task().getTitle() + "\n" + entry.start() + " → " + entry.end()));
        box.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { owner.showDetails(entry); e.consume(); }
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) { owner.deleteTask(entry.date(), entry.task()); e.consume(); }
        });
        double[] anchor = new double[2]; int[] proposed = {entry.task().getStartMin(), entry.task().getDuration(), 0};
        boolean[] moved = {false}, resizing = {false}, cancelled = {false};
        box.setOnMouseMoved(e -> box.setCursor(e.getY() >= box.getHeight() - 6 ? javafx.scene.Cursor.S_RESIZE : javafx.scene.Cursor.HAND));
        box.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            box.requestFocus(); anchor[0] = e.getSceneX(); anchor[1] = timeline.sceneToLocal(e.getSceneX(), e.getSceneY()).getY(); moved[0] = false; cancelled[0] = false;
            proposed[0] = entry.task().getStartMin(); proposed[1] = entry.task().getDuration(); proposed[2] = 0;
            resizing[0] = e.getY() >= box.getHeight() - 6 && entry.end().toLocalDate().equals(day) ||
                    e.getY() >= box.getHeight() - 6 && entry.end().equals(day.plusDays(1).atStartOfDay());
            gesture = true; cancelGesture = () -> { cancelled[0] = true; endGesture(); render(); };
            e.consume();
        });
        box.setOnMouseDragged(e -> {
            if (!gesture || cancelled[0]) return;
            double deltaY = timeline.sceneToLocal(e.getSceneX(), e.getSceneY()).getY() - anchor[1];
            if (!moved[0] && Math.abs(deltaY) < 3 && Math.abs(e.getSceneX() - anchor[0]) < 3) return;
            moved[0] = true;
            followPointer(e.getSceneY(), () -> {
            double delta = (timeline.sceneToLocal(e.getSceneX(), e.getSceneY()).getY() - anchor[1]) / minuteHeight;
            if (resizing[0]) {
                proposed[1] = owner.preferences().snapDuration(entry.task().getStartMin(), entry.task().getDuration(), delta, e.isAltDown());
                fixed(box, columnWidth - 6, Math.max(1, end - start + proposed[1] - entry.task().getDuration()) * minuteHeight);
            } else {
                proposed[0] = owner.preferences().snapStart(entry.task().getStartMin() + delta, e.isAltDown());
                proposed[2] = Math.max(-dayIndex, Math.min(dayCount - 1 - dayIndex, (int)Math.round((e.getSceneX() - anchor[0]) / dayWidth)));
                AnchorPane.setTopAnchor(box, top + (proposed[0] - entry.task().getStartMin()) * minuteHeight);
                AnchorPane.setLeftAnchor(box, left + proposed[2] * dayWidth);
            }
            clock.setText(time(proposed[0]) + " · " + proposed[1] + " min");
            });
            e.consume();
        });
        box.setOnMouseReleased(e -> {
            if (e.getButton() != MouseButton.PRIMARY || cancelled[0]) return;
            endGesture();
            if (moved[0]) owner.moveOccurrence(entry, entry.date().plusDays(proposed[2]), proposed[0], proposed[1]);
            else owner.showDetails(entry);
            e.consume();
        });
        box.setOnContextMenuRequested(e -> { owner.showDetails(entry); e.consume(); });
    }

    private void installRangeCreation() {
        double[] start = new double[2]; boolean[] selecting = {false};
        Region selection = new Region(); selection.getStyleClass().add("calendar-range-selection"); selection.setMouseTransparent(true);
        timeline.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getTarget() != timeline) return;
            start[0] = e.getX(); start[1] = e.getY(); selecting[0] = true; gesture = true;
            cancelGesture = () -> { selecting[0] = false; endGesture(); render(); };
        });
        timeline.setOnMouseDragged(e -> {
            if (!selecting[0]) return;
            followPointer(e.getSceneY(), () -> {
            double endY = timeline.sceneToLocal(e.getSceneX(), e.getSceneY()).getY();
            int column = Math.max(0, Math.min(dayCount - 1, (int)(start[0] / dayWidth)));
            if (!timeline.getChildren().contains(selection)) timeline.getChildren().add(selection);
            AnchorPane.setLeftAnchor(selection, column * dayWidth + 3); AnchorPane.setTopAnchor(selection, Math.max(0, Math.min(start[1], endY)));
            fixed(selection, dayWidth - 6, Math.abs(endY - start[1]));
            });
        });
        timeline.setOnMouseReleased(e -> {
            if (!selecting[0]) return;
            selecting[0] = false; endGesture(); timeline.getChildren().remove(selection);
            int step = e.isAltDown() ? 1 : owner.preferences().snapStepMinutes();
            int minute = Math.max(0, Math.min(1440 - step, (int)(Math.min(start[1], e.getY()) / minuteHeight / step) * step));
            int duration = Math.abs(e.getY() - start[1]) < 4 ? 60 : Math.max(step, (int)Math.round(Math.abs(e.getY() - start[1]) / minuteHeight / step) * step);
            int column = Math.max(0, Math.min(dayCount - 1, (int)(start[0] / dayWidth)));
            owner.createEvent(first.plusDays(column), minute, Math.min(1440 - minute, duration));
        });
        timeline.setOnMouseClicked(null);
    }

    private void renderMonth() {
        alternateContent.getChildren().clear();
        GridPane grid = new GridPane(); grid.getStyleClass().add("calendar-month-grid");
        for (int i = 0; i < 7; i++) { ColumnConstraints column = new ColumnConstraints(); column.setPercentWidth(100.0 / 7); grid.getColumnConstraints().add(column); }
        LocalDate start = owner.weekStart(owner.selectedDate().withDayOfMonth(1));
        List<Occurrence> events = CalendarOccurrenceService.between(owner.tasksSnapshot(), start, start.plusDays(41));
        for (int index = 0; index < 42; index++) {
            LocalDate date = start.plusDays(index);
            VBox cell = new VBox(5); cell.getStyleClass().add("calendar-month-cell"); cell.setMinWidth(0); cell.setMinHeight(112);
            if (date.getMonth() != owner.selectedDate().getMonth()) cell.getStyleClass().add("calendar-month-outside");
            if (date.equals(LocalDate.now())) cell.getStyleClass().add("calendar-month-today");
            Button day = button(date.format(DateTimeFormatter.ofPattern(index < 7 ? "EEE d" : "d")), "calendar-month-date", () -> owner.selectDay(date));
            cell.getChildren().add(day);
            List<Occurrence> entries = events.stream().filter(o -> intersects(o, date)).toList();
            entries.stream().limit(3).forEach(o -> cell.getChildren().add(eventButton(o, "calendar-month-event")));
            if (entries.size() > 3) cell.getChildren().add(button("+" + (entries.size() - 3) + " more", "text-button", () -> owner.showAgenda(date)));
            cell.setOnMouseClicked(e -> { if (e.getTarget() == cell) owner.createEvent(date, 9 * 60, 60); });
            grid.add(cell, index % 7, index / 7);
        }
        alternateContent.getChildren().add(grid);
    }

    private void renderAgenda() {
        alternateContent.getChildren().clear();
        List<Occurrence> events = CalendarOccurrenceService.between(owner.tasksSnapshot(), owner.selectedDate(), owner.selectedDate().plusDays(89));
        ListView<Occurrence> list = new ListView<>(javafx.collections.FXCollections.observableArrayList(events));
        list.setId("calendarAgendaList"); list.setPrefHeight(Math.max(420, view.getHeight() - 190));
        list.setPlaceholder(label("No appointments in the next 90 days. Plan something with + New.", "empty-activities"));
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Occurrence item, boolean empty) {
                super.updateItem(item, empty); setText(null); setGraphic(null);
                if (empty || item == null) return;
                Button entry = eventButton(item, "calendar-agenda-event");
                entry.setText(item.date().format(DateTimeFormatter.ofPattern("EEE d MMM")) + "    " +
                        (item.task().isAllDay() ? "All day" : time(item.task().getStartMin())) + "    " + item.task().getTitle());
                setGraphic(entry);
            }
        });
        alternateContent.getChildren().addAll(label("NEXT 90 DAYS", "page-eyebrow"), list);
    }
    private Button eventButton(Occurrence entry, String style) {
        Button button = button(entry.task().getTitle(), style, () -> owner.showDetails(entry));
        button.getStyleClass().add("task-" + safeColor(entry.task())); button.setMaxWidth(Double.MAX_VALUE);
        button.setMinWidth(0); button.setTextOverrun(OverrunStyle.ELLIPSIS);
        button.setTooltip(new Tooltip(entry.task().getTitle() + "\n" + entry.start() + " → " + entry.end()));
        return button;
    }
    private void put(Region node, double x, double y, double width, double height) {
        node.setMouseTransparent(true); fixed(node, width, height);
        AnchorPane.setLeftAnchor(node, x); AnchorPane.setTopAnchor(node, y); timeline.getChildren().add(node);
    }
    static boolean intersects(Occurrence entry, LocalDate date) { return entry.start().isBefore(date.plusDays(1).atStartOfDay()) && entry.end().isAfter(date.atStartOfDay()); }
    static String time(int minutes) { return String.format("%02d:%02d", minutes / 60, minutes % 60); }
    private static String safeColor(Task task) { String color = task.getColor().toLowerCase(Locale.ROOT); return List.of("red", "green", "yellow", "orange", "purple").contains(color) ? color : "blue"; }
    private static Label label(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); return label; }
    private static Button button(String text, String style, Runnable action) { Button button = new Button(text); button.getStyleClass().add(style); button.setOnAction(e -> action.run()); return button; }
    private static Region space() { Region region = new Region(); HBox.setHgrow(region, Priority.ALWAYS); return region; }
    private static void fixed(Region region, double width, double height) { region.setMinSize(width, height); region.setPrefSize(width, height); region.setMaxSize(width, height); }
    private static void visible(Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
}
