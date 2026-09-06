package com.crm.controller;

import com.crm.model.*;
import com.crm.service.ThemeService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.*;

/** A person's work and conversation history, without mixing edits with navigation. */
final class ContactDetailController {
    interface Actions {
        Map<LocalDate, List<Task>> tasks(); List<Note> notes();
        void edit(Contact contact); void changed(); void followUp(Contact contact); void newNote(Contact contact);
        void openTask(LocalDate date, Task task); void openNote(String id);
    }
    private final ThemeService theme;
    private final Actions actions;
    ContactDetailController(ThemeService theme, Actions actions) { this.theme = theme; this.actions = actions; }
    void show(Contact contact) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Contact details"); theme.applyTo(dialog); dialog.setResizable(true);
        VBox content = new VBox(16); content.getStyleClass().add("contact-profile"); content.setPrefWidth(670);
        Runnable[] refresh = new Runnable[1];
        Runnable render = () -> {
            content.getChildren().clear();
            Label name = label(contact.nameProperty().get(), "section-title"); name.setWrapText(true);
            Label company = label(contact.titleProperty().get() + " · " + contact.companyProperty().get(), "section-subtitle");
            Label address = label(contact.emailProperty().get() + "    " + contact.phoneProperty().get(), "section-subtitle"); address.setWrapText(true);
            content.getChildren().addAll(new VBox(5, name, company, address));
            Button edit = button("Edit details", () -> { actions.edit(contact); refresh[0].run(); });
            Button follow = button("+ Follow-up", () -> { actions.followUp(contact); refresh[0].run(); }); follow.getStyleClass().add("btn-primary");
            Button note = button("New meeting note", () -> { dialog.close(); actions.newNote(contact); });
            content.getChildren().add(new FlowPane(8, 8, follow, note, edit));
            if (!contact.descriptionProperty().get().isBlank()) { Label description = new Label(contact.descriptionProperty().get()); description.setWrapText(true); content.getChildren().add(description); }
            TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            VBox timeline = new VBox(10);
            timeline.getChildren().add(button("Log an interaction", () -> {
                interaction(contact).ifPresent(item -> { contact.addInteraction(item); actions.changed(); refresh[0].run(); });
            }));
            if (contact.getInteractions().isEmpty()) timeline.getChildren().add(label("Log a call, email or meeting to build this contact's history.", "empty-activities"));
            contact.getInteractions().stream().sorted(Comparator.comparing(ContactInteraction::date).reversed()).forEach(item -> {
                Label summary = new Label(item.summary()); summary.setWrapText(true);
                VBox row = new VBox(4, label(item.date() + " · " + item.kind(), "overview-card-title"), summary); row.getStyleClass().add("contact-timeline-entry"); timeline.getChildren().add(row);
            });
            VBox work = new VBox(8), notes = new VBox(8);
            actions.tasks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entry.getValue().stream()
                    .filter(task -> task.getContactId().equals(contact.getId())).forEach(task -> {
                        String schedule = !task.isScheduled() ? task.getDueDate() == null ? "No deadline" : "Due " + task.getDueDate() : entry.getKey().toString();
                        Button item = button(task.getTitle() + "\n" + schedule + " · " + task.getStatus(), () -> { dialog.close(); actions.openTask(entry.getKey(), task); });
                        item.setWrapText(true); item.setMaxWidth(Double.MAX_VALUE); item.getStyleClass().add("contact-linked-record"); work.getChildren().add(item);
                    }));
            actions.notes().stream().filter(item -> item.getContactId().equals(contact.getId())).forEach(item -> {
                Button linked = button(item.getTitle(), () -> { dialog.close(); actions.openNote(item.getId()); });
                linked.setMaxWidth(Double.MAX_VALUE); linked.getStyleClass().add("contact-linked-record"); notes.getChildren().add(linked);
            });
            if (work.getChildren().isEmpty()) work.getChildren().add(label("No linked work yet. Create a follow-up to get started.", "empty-activities"));
            if (notes.getChildren().isEmpty()) notes.getChildren().add(label("Notes linked to this contact appear here.", "empty-activities"));
            tabs.getTabs().setAll(new Tab("Timeline", scroller(timeline)), new Tab("Tasks & appointments", scroller(work)), new Tab("Notes", scroller(notes)));
            content.getChildren().add(tabs);
        };
        refresh[0] = render;
        render.run(); dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }
    private Optional<ContactInteraction> interaction(Contact contact) {
        Dialog<ContactInteraction> dialog = new Dialog<>(); dialog.setTitle("Log interaction"); dialog.setHeaderText(contact.nameProperty().get()); theme.applyTo(dialog);
        DatePicker date = new DatePicker(LocalDate.now()); ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList("Call", "Email", "Meeting", "Note")); kind.setValue("Call");
        TextArea summary = new TextArea(); summary.setPromptText("What happened, and what comes next?"); summary.setWrapText(true); summary.setPrefRowCount(5);
        Label error = label("", "form-error"); VBox form = new VBox(10, new HBox(8, date, kind), summary, error); form.getStyleClass().add("event-editor");
        dialog.getDialogPane().setContent(form); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (date.getValue() == null || summary.getText().isBlank()) { error.setText("Choose a date and describe the interaction."); e.consume(); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? new ContactInteraction(date.getValue(), kind.getValue(), summary.getText().trim()) : null);
        return dialog.showAndWait();
    }
    private static ScrollPane scroller(VBox box) { box.getStyleClass().add("contact-profile-tab"); ScrollPane pane = new ScrollPane(box); pane.setFitToWidth(true); pane.setPrefViewportHeight(320); return pane; }
    private static Button button(String text, Runnable action) { Button button = new Button(text); button.getStyleClass().add("btn-secondary"); button.setOnAction(e -> action.run()); return button; }
    private static Label label(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); label.setWrapText(true); return label; }
}
