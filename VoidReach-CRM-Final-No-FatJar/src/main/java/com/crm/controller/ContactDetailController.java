package com.crm.controller;

import com.crm.model.*;
import com.crm.service.ThemeService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

import static com.crm.controller.DetailDialogLayout.*;

/** A person's work and conversation history, without mixing edits with navigation. */
final class ContactDetailController {
    interface Actions {
        Map<LocalDate, List<Task>> tasks(); List<Note> notes();
        void edit(Contact contact); void changed(); void followUp(Contact contact); void newNote(Contact contact);
        void openTask(LocalDate date, Task task); void openNote(String id);
    }
    private final ThemeService theme;
    private final Actions actions;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    ContactDetailController(ThemeService theme, Actions actions) { this.theme = theme; this.actions = actions; }
    void show(Contact contact) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Contact details"); theme.applyTo(dialog); configure(dialog, 700);
        VBox content = new VBox(18); content.getStyleClass().add("contact-profile"); content.setMinWidth(0);
        int[] selectedTab = { 0 };
        Runnable[] refresh = new Runnable[1];
        Runnable render = () -> {
            content.getChildren().clear();
            Label name = label(contact.nameProperty().get(), "contact-profile-name");
            VBox identity = new VBox(5, name); identity.setMinWidth(0); HBox.setHgrow(identity, Priority.ALWAYS);
            String company = String.join(" · ", java.util.stream.Stream.of(contact.titleProperty().get(), contact.companyProperty().get())
                    .filter(value -> value != null && !value.isBlank()).toList());
            if (!company.isBlank()) identity.getChildren().add(label(company, "detail-dialog-subtitle"));
            if (contact.tagsProperty().get() != null && !contact.tagsProperty().get().isBlank()) identity.getChildren().add(label(contact.tagsProperty().get(), "contact-profile-badge"));
            Label avatar = label(initials(contact.nameProperty().get()), "contact-profile-initials");
            avatar.setAlignment(Pos.CENTER);
            HBox profileHeader = new HBox(16, avatar, identity); profileHeader.setAlignment(Pos.CENTER_LEFT);
            profileHeader.getStyleClass().add("detail-dialog-header");
            dialog.getDialogPane().setHeader(profileHeader);
            content.getChildren().add(columns(channel("far-envelope", "Email", contact.emailProperty().get()),
                    channel("fas-phone", "Phone", contact.phoneProperty().get())));
            Button edit = button("Edit details", () -> { actions.edit(contact); refresh[0].run(); });
            edit.setGraphic(icon("fas-pen"));
            Button follow = button("Follow-up", () -> { actions.followUp(contact); refresh[0].run(); });
            follow.getStyleClass().remove("btn-secondary"); follow.getStyleClass().add("btn-primary"); follow.setGraphic(icon("fas-plus"));
            Button note = button("New meeting note", () -> { dialog.close(); actions.newNote(contact); });
            note.setGraphic(icon("far-file-alt"));
            content.getChildren().add(new FlowPane(10, 8, follow, note, edit));
            if (!contact.descriptionProperty().get().isBlank()) content.getChildren().add(section("About", label(contact.descriptionProperty().get(), "contact-about")));
            TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getStyleClass().add("contact-profile-tabs");
            VBox timeline = new VBox(10);
            Button log = button("Log an interaction", () -> {
                interaction(contact).ifPresent(item -> { contact.addInteraction(item); actions.changed(); refresh[0].run(); });
            });
            log.setGraphic(icon("fas-plus"));
            HBox timelineActions = new HBox(log); timelineActions.setAlignment(Pos.CENTER_RIGHT); timeline.getChildren().add(timelineActions);
            if (contact.getInteractions().isEmpty()) timeline.getChildren().add(empty("far-comment-dots", "Start the conversation history",
                    "Log a call, email or meeting to keep the next steps in view."));
            contact.getInteractions().stream().sorted(Comparator.comparing(ContactInteraction::date).reversed()).forEach(item -> {
                Label kind = label(item.kind(), "detail-record-title");
                Label date = label(DATE.format(item.date()), "detail-record-subtitle");
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox heading = new HBox(8, kind, spacer, date); heading.setAlignment(Pos.CENTER_LEFT);
                VBox copy = new VBox(7, heading, label(item.summary(), "contact-interaction-summary")); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
                HBox row = new HBox(12, icon(interactionIcon(item.kind())), copy);
                row.getStyleClass().add("contact-timeline-entry"); timeline.getChildren().add(row);
            });
            VBox work = new VBox(8), notes = new VBox(8);
            actions.tasks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entry.getValue().stream()
                    .filter(task -> task.getContactId().equals(contact.getId())).forEach(task -> {
                        String schedule = !task.isScheduled() ? task.getDueDate() == null ? "No deadline" : "Due " + DATE.format(task.getDueDate())
                                : DATE.format(entry.getKey()) + (task.isAllDay() ? " · All day" : " · " + CalendarBoard.time(task.getStartMin()));
                        String status = task.getStatus() == Task.Status.TODO ? "To do" : task.getStatus().toString().toLowerCase(Locale.ROOT).replace('_', ' ');
                        work.getChildren().add(linkedRecord(task.isScheduled() ? "far-calendar-alt" : "fas-tasks", task.getTitle(),
                                schedule + " · " + status, () -> { dialog.close(); actions.openTask(entry.getKey(), task); }));
                    }));
            actions.notes().stream().filter(item -> item.getContactId().equals(contact.getId())).forEach(item -> {
                notes.getChildren().add(linkedRecord("far-file-alt", item.getTitle(), "Linked note", () -> { dialog.close(); actions.openNote(item.getId()); }));
            });
            int taskCount = work.getChildren().size(), noteCount = notes.getChildren().size();
            if (taskCount == 0) work.getChildren().add(empty("far-calendar-alt", "No linked work yet", "Create a follow-up to plan your next step with this contact."));
            if (noteCount == 0) notes.getChildren().add(empty("far-file-alt", "A place for shared context", "Meeting notes linked to this contact will appear here."));
            tabs.getTabs().setAll(tab("Timeline", contact.getInteractions().size(), timeline), tab("Tasks & appointments", taskCount, work), tab("Notes", noteCount, notes));
            tabs.getSelectionModel().select(selectedTab[0]);
            tabs.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> selectedTab[0] = b.intValue());
            content.getChildren().add(tabs);
        };
        refresh[0] = render;
        render.run(); dialog.getDialogPane().setContent(scroller(content, 560)); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }
    private Optional<ContactInteraction> interaction(Contact contact) {
        Dialog<ContactInteraction> dialog = new Dialog<>(); dialog.setTitle("Log interaction"); theme.applyTo(dialog); configure(dialog, 540);
        dialog.getDialogPane().setHeader(header("far-comment-dots", "Log an interaction", contact.nameProperty().get()));
        DatePicker date = new DatePicker(LocalDate.now()); ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("Call", "Email", "Meeting", "Note")); kind.setValue("Call");
        TextArea summary = new TextArea(); summary.setPromptText("What happened, and what comes next?"); summary.setWrapText(true); summary.setPrefRowCount(5);
        Label error = label("", "form-error"); error.setVisible(false); error.setManaged(false);
        VBox form = new VBox(16, columns(field("Date", date), field("Interaction", kind)), field("Summary & next steps", summary), error); form.getStyleClass().add("event-editor");
        dialog.getDialogPane().setContent(form); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (date.getValue() == null || summary.getText().isBlank()) { error.setText("Choose a date and describe the interaction."); error.setVisible(true); error.setManaged(true); e.consume(); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? new ContactInteraction(date.getValue(), kind.getValue(), summary.getText().trim()) : null);
        return dialog.showAndWait();
    }
    private static Tab tab(String title, int count, VBox content) {
        content.getStyleClass().add("contact-profile-tab");
        Tab tab = new Tab(title, content);
        tab.setGraphic(label(Integer.toString(count), "detail-count"));
        return tab;
    }
    private static VBox channel(String literal, String caption, String value) {
        Label text = label(value == null || value.isBlank() ? "Not added" : value, "contact-channel-value");
        if (value == null || value.isBlank()) text.getStyleClass().add("detail-dialog-subtitle");
        HBox heading = new HBox(7, icon(literal), label(caption, "contact-channel-caption")); heading.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(8, heading, text); card.getStyleClass().add("contact-channel"); return card;
    }
    private static VBox empty(String literal, String title, String description) {
        VBox empty = new VBox(10, icon(literal), label(title, "detail-empty-title"), label(description, "detail-empty-copy"));
        empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("detail-empty");
        return empty;
    }
    private static Button linkedRecord(String literal, String title, String detail, Runnable action) {
        Button button = button("", action); button.getStyleClass().add("contact-linked-record"); button.setMaxWidth(Double.MAX_VALUE);
        VBox copy = new VBox(5, label(title, "detail-record-title"), label(detail, "detail-record-subtitle")); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(12, icon(literal), copy, icon("fas-chevron-right")); row.setAlignment(Pos.CENTER_LEFT);
        row.prefWidthProperty().bind(button.widthProperty().subtract(32));
        button.setGraphic(row); button.setAccessibleText(title + ". " + detail); return button;
    }
    private static String interactionIcon(String kind) {
        return switch (kind) { case "Call" -> "fas-phone"; case "Email" -> "far-envelope"; case "Meeting" -> "far-calendar-alt"; default -> "far-file-alt"; };
    }
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] words = name.strip().split("\\s+");
        String first = words[0].substring(0, words[0].offsetByCodePoints(0, 1));
        String last = words.length == 1 ? "" : words[words.length - 1].substring(0, words[words.length - 1].offsetByCodePoints(0, 1));
        return (first + last).toUpperCase(Locale.ROOT);
    }
    private static Button button(String text, Runnable action) { Button button = new Button(text); button.getStyleClass().add("btn-secondary"); button.setOnAction(e -> action.run()); return button; }
}
