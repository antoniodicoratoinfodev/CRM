package com.crm.controller;

import com.crm.model.*;
import com.crm.service.ThemeService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.crm.controller.DetailDialogLayout.*;

/** Validates before the dialog closes; cancelling never changes records or note links. */
final class CalendarTaskEditor {
    record Result(LocalDate date, Task task, Set<String> notes, boolean delete) { }
    private final ThemeService theme;
    private final CalendarController.NoteIntegration noteIntegration;
    private final List<Contact> contacts;
    CalendarTaskEditor(ThemeService theme, CalendarController.NoteIntegration notes, List<Contact> contacts) {
        this.theme = theme; this.noteIntegration = notes; this.contacts = contacts;
    }
    Optional<Result> show(Task original, LocalDate date, int minute, int duration, String description, boolean newTodo) {
        Dialog<Result> dialog = new Dialog<>(); dialog.setTitle(original == null ? "New Task" : "Edit Task"); theme.applyTo(dialog);
        configure(dialog, 620);
        dialog.getDialogPane().setMinWidth(560);
        dialog.getDialogPane().setHeader(header("far-calendar-alt", original == null ? "Plan something new" : "Task details",
                "Keep the schedule, context and next steps together."));
        TextField title = new TextField(original == null ? "" : original.getTitle()); title.setId("eventTitleField");
        title.setPromptText("What are you planning?"); title.getStyleClass().add("event-editor-title");
        ComboBox<String> kind = combo(List.of("Appointment", "All-day event", "Task"),
                original == null ? (newTodo ? "Task" : "Appointment") : !original.isScheduled() ? "Task" : original.isAllDay() ? "All-day event" : "Appointment");
        kind.setId("eventKindCombo");
        DatePicker startDate = new DatePicker(date), endDate = new DatePicker(date.atStartOfDay().plusMinutes(minute + duration).toLocalDate());
        startDate.setId("eventStartDate"); endDate.setId("eventEndDate");
        if (original != null && original.isAllDay()) endDate.setValue(date.plusDays(Math.max(0, duration / 1440 - 1)));
        TextField startTime = new TextField(CalendarBoard.time(minute)); startTime.setId("eventStartTime"); startTime.setPrefColumnCount(6);
        TextField endTime = new TextField(CalendarBoard.time((minute + duration) % 1440)); endTime.setId("eventEndTime"); endTime.setPrefColumnCount(6);
        DatePicker dueDate = new DatePicker(original == null ? null : original.getDueDate()); dueDate.setPromptText("No deadline");
        startTime.setPrefWidth(76); endTime.setPrefWidth(76);
        startTime.setMinWidth(76); endTime.setMinWidth(76);
        HBox startRow = new HBox(8, startDate, startTime), endRow = new HBox(8, endDate, endTime);
        HBox.setHgrow(startDate, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(endDate, javafx.scene.layout.Priority.ALWAYS);
        startDate.setMaxWidth(Double.MAX_VALUE); endDate.setMaxWidth(Double.MAX_VALUE);
        startDate.setMinWidth(0); endDate.setMinWidth(0);
        startTime.setAccessibleText("Start time, HH:mm"); endTime.setAccessibleText("End time, HH:mm");
        HBox schedule = columns(field("Starts", startRow), field("Ends / through", endRow));
        VBox deadline = field("Deadline (optional)", dueDate);
        TextArea body = new TextArea(original == null ? description : original.getDescription()); body.setPrefRowCount(3); body.setWrapText(true);
        body.setPromptText("Context, location, meeting link or next steps…");
        ComboBox<Task.Priority> priority = combo(List.of(Task.Priority.values()), original == null ? Task.Priority.NORMAL : original.getPriority());
        ComboBox<Task.Status> status = combo(List.of(Task.Status.values()), original == null ? Task.Status.TODO : original.getStatus());
        ComboBox<Contact> contact = new ComboBox<>(FXCollections.observableArrayList(contacts)); contact.setPromptText("No linked contact"); contact.setMaxWidth(Double.MAX_VALUE);
        contact.setConverter(new StringConverter<>() {
            @Override public String toString(Contact value) { return value == null ? "No linked contact" : value.nameProperty().get()
                    + (value.companyProperty().get() == null || value.companyProperty().get().isBlank() ? "" : " · " + value.companyProperty().get()); }
            @Override public Contact fromString(String value) { return null; }
        });
        if (original != null) contacts.stream().filter(c -> c.getId().equals(original.getContactId())).findFirst().ifPresent(contact::setValue);
        Button clearContact = new Button("Clear"); clearContact.getStyleClass().add("text-button"); clearContact.setOnAction(e -> contact.setValue(null));
        clearContact.disableProperty().bind(contact.valueProperty().isNull()); clearContact.setMinWidth(Region.USE_PREF_SIZE);
        HBox contactRow = new HBox(8, contact, clearContact); contactRow.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(contact, javafx.scene.layout.Priority.ALWAYS);
        ComboBox<Task.Frequency> frequency = combo(List.of(Task.Frequency.values()), original == null ? Task.Frequency.NONE : original.getFrequency());
        Spinner<Integer> interval = new Spinner<>(1, 999, original == null ? 1 : original.getRepeatInterval()); interval.setEditable(false); interval.setPrefWidth(85);
        Spinner<Integer> count = new Spinner<>(0, 100000, original == null ? 0 : original.getRepeatCount()); count.setEditable(false); count.setPrefWidth(100);
        DatePicker until = new DatePicker(original == null ? null : original.getRepeatUntil()); until.setPromptText("No end date");
        ComboBox<Integer> reminder = combo(List.of(-1, 0, 5, 10, 15, 30, 60, 1440), original == null ? -1 : original.getReminderMinutes());
        reminder.setConverter(new StringConverter<>() {
            @Override public String toString(Integer value) { return value == null || value < 0 ? "No reminder" : value == 0 ? "At start" : value == 1440 ? "1 day before" : value + " min before"; }
            @Override public Integer fromString(String value) { return -1; }
        });
        ComboBox<String> color = combo(List.of("Blue", "Green", "Purple", "Orange", "Red", "Yellow"), original == null ? "Blue" : original.getColor());
        Set<String> linked = new LinkedHashSet<>();
        if (original != null) noteIntegration.notesForTask(original.getId()).forEach(note -> linked.add(note.getId()));
        TextField noteSearch = new TextField(); noteSearch.setPromptText("Find a note or folder…");
        ListView<Note> noteList = new ListView<>(); noteList.setPrefHeight(145);
        noteList.setPlaceholder(hint("No matching notes. Create a note in Notes to link it here."));
        Map<String, String> folders = new HashMap<>(); noteIntegration.folders().forEach(folder -> folders.put(folder.getId(), folder.getName()));
        noteList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Note item, boolean empty) {
                super.updateItem(item, empty); setText(null); setGraphic(null);
                if (empty || item == null) return;
                CheckBox choice = new CheckBox(folders.getOrDefault(item.getFolderId(), "Notes") + " / " + item.getTitle());
                choice.setSelected(linked.contains(item.getId()));
                choice.setOnAction(e -> { if (choice.isSelected()) linked.add(item.getId()); else linked.remove(item.getId()); });
                setGraphic(choice);
            }
        });
        Runnable filterNotes = () -> noteList.setItems(FXCollections.observableArrayList(noteIntegration.notes().stream().filter(note ->
                (note.getTitle() + " " + folders.getOrDefault(note.getFolderId(), "")).toLowerCase(Locale.ROOT).contains(noteSearch.getText().toLowerCase(Locale.ROOT))).toList()));
        noteSearch.textProperty().addListener((o, a, b) -> filterNotes.run()); filterNotes.run();
        VBox recurrence = new VBox(12, columns(field("Repeat", frequency), field("Every", interval)),
                columns(field("Occurrences · 0 = unlimited", count), field("Repeat until (optional)", until)),
                field("Reminder", reminder), hint("Monthly repeats skip months without that date. Reminders run while VoidReach is open."));
        TitledPane advanced = new TitledPane("Repeat & reminders", recurrence); advanced.setExpanded(false);
        TitledPane notes = new TitledPane("Linked notes", new VBox(8, noteSearch, noteList)); notes.setExpanded(false);
        advanced.getStyleClass().add("detail-disclosure"); notes.getStyleClass().add("detail-disclosure");
        Label error = hint(""); error.getStyleClass().add("form-error"); error.setId("eventValidationError");
        Region scheduleSpacer = new Region(); HBox.setHgrow(scheduleSpacer, javafx.scene.layout.Priority.ALWAYS);
        kind.setAccessibleText("Item type"); kind.setPrefWidth(180);
        HBox scheduleHeading = new HBox(12, label("Schedule", "detail-section-title"), scheduleSpacer, kind);
        scheduleHeading.setAlignment(Pos.CENTER_LEFT);
        VBox scheduleSection = new VBox(12, scheduleHeading, schedule, deadline);
        scheduleSection.getStyleClass().add("detail-section");
        VBox content = new VBox(16, field("Title", title),
                scheduleSection,
                section("Details", columns(field("Priority", priority), field("Status", status), field("Color", color)),
                        field("Linked contact", contactRow), field("Description", body)),
                new VBox(8, advanced, notes));
        content.getStyleClass().add("event-editor"); content.setMinWidth(0);
        Runnable updateKind = () -> {
            boolean task = kind.getValue().equals("Task"), allDay = kind.getValue().equals("All-day event");
            schedule.setVisible(!task); schedule.setManaged(!task); deadline.setVisible(task); deadline.setManaged(task);
            startTime.setDisable(allDay); endTime.setDisable(allDay); advanced.setDisable(task);
        };
        kind.valueProperty().addListener((o, a, b) -> updateKind.run()); updateKind.run();
        ScrollPane scroller = scroller(content, 600);
        error.setVisible(false); error.setManaged(false); error.setPadding(new javafx.geometry.Insets(8, 20, 0, 20));
        VBox shell = new VBox(scroller, error); VBox.setVgrow(scroller, javafx.scene.layout.Priority.ALWAYS);
        dialog.getDialogPane().setContent(shell);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE), delete = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        if (original != null) dialog.getDialogPane().getButtonTypes().add(delete);
        dialog.getDialogPane().lookupButton(save).getStyleClass().add("btn-primary");
        if (original != null) dialog.getDialogPane().lookupButton(delete).getStyleClass().add("danger-button");
        Result[] accepted = new Result[1];
        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            try {
                if (title.getText().isBlank()) throw new IllegalArgumentException("Give this item a title.");
                boolean task = kind.getValue().equals("Task"), allDay = kind.getValue().equals("All-day event");
                LocalDate anchor = task ? Objects.requireNonNullElse(dueDate.getValue(), date) : startDate.getValue();
                if (anchor == null || !task && endDate.getValue() == null) throw new IllegalArgumentException("Choose start and end dates.");
                int from = task || allDay ? 0 : parseTime(startTime.getText(), false);
                long length = task ? 60 : allDay ? (ChronoUnit.DAYS.between(anchor, endDate.getValue()) + 1) * 1440 :
                        ChronoUnit.MINUTES.between(anchor.atStartOfDay().plusMinutes(from), endDate.getValue().atStartOfDay().plusMinutes(parseTime(endTime.getText(), true)));
                if (length < 1 || length > 366 * 1440) throw new IllegalArgumentException("The end must follow the start, within 366 days.");
                if (!task && until.getValue() != null && until.getValue().isBefore(anchor)) throw new IllegalArgumentException("The repeat end date cannot precede the first event.");
                if (!task && frequency.getValue() != Task.Frequency.NONE && until.getValue() != null && count.getValue() > 0)
                    throw new IllegalArgumentException("Choose a repeat end date OR a number of occurrences, not both.");
                Task result = Task.scheduled(original == null ? UUID.randomUUID().toString() : original.getId(), title.getText().trim(), body.getText(), from, (int)length, color.getValue(), false);
                if (original != null) result.applyMetadata(original.metadata());
                result.setScheduled(!task); result.setAllDay(allDay); result.setDueDate(task ? dueDate.getValue() : null);
                result.setPriority(priority.getValue()); result.setStatus(status.getValue());
                result.setContactId(contact.getValue() == null ? "" : contact.getValue().getId());
                result.setFrequency(task ? Task.Frequency.NONE : frequency.getValue()); result.setRepeatInterval(interval.getValue());
                result.setRepeatCount(count.getValue()); result.setRepeatUntil(until.getValue()); result.setReminderMinutes(task ? -1 : reminder.getValue());
                accepted[0] = new Result(anchor, result, Set.copyOf(linked), false);
            } catch (RuntimeException invalid) { error.setText(invalid.getMessage()); error.setVisible(true); error.setManaged(true); e.consume(); }
        });
        dialog.setResultConverter(button -> button == save ? accepted[0] : button == delete ? new Result(date, original, Set.copyOf(linked), true) : null);
        dialog.setOnShown(e -> { title.requestFocus(); title.selectAll(); });
        return dialog.showAndWait();
    }
    static int parseTime(String text, boolean allowMidnight) {
        if (text == null || !text.trim().matches("\\d{1,2}:\\d{2}")) throw new IllegalArgumentException("Enter times as HH:mm, for example 09:30.");
        String[] parts = text.trim().split(":"); int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
        if (hour > 23 && !(allowMidnight && hour == 24 && minute == 0) || minute > 59)
            throw new IllegalArgumentException("Use 00:00–23:59 (24:00 is allowed for the end).");
        return hour * 60 + minute;
    }
    private static <T> ComboBox<T> combo(List<T> values, T value) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values)); box.setValue(value);
        if (value instanceof Enum<?>) box.setConverter(new StringConverter<>() {
            @Override public String toString(T item) {
                if (item == null) return "";
                if (item == Task.Status.TODO) return "To do";
                String text = item.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
                return Character.toUpperCase(text.charAt(0)) + text.substring(1);
            }
            @Override public T fromString(String text) { return value; }
        });
        return box;
    }
    private static Label hint(String text) { Label label = new Label(text); label.setWrapText(true); label.getStyleClass().add("section-subtitle"); return label; }
}
