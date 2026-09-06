package com.crm.controller;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;
import java.util.function.Consumer;

/** Owns sidebar selection and switching between the main application views. */
public final class NavigationController {
    private final Node homeView;
    private final Node dashboardView;
    private final VBox contactsView;
    private final VBox calendarView;
    private final VBox tasksView;
    private final VBox notesView;
    private final Node settingsView;
    private final VBox genericView;
    private final Label genericTitle;
    private final FontIcon genericIcon;
    private final VBox sidebarContainer;
    private Consumer<String> onNavigate = title -> { };

    public NavigationController(Node homeView, Node dashboardView,
                                VBox contactsView, VBox calendarView, VBox tasksView, VBox notesView,
                                Node settingsView, VBox genericView,
                                Label genericTitle, FontIcon genericIcon, VBox sidebarContainer) {
        this.homeView = Objects.requireNonNull(homeView);
        this.dashboardView = Objects.requireNonNull(dashboardView);
        this.contactsView = Objects.requireNonNull(contactsView);
        this.calendarView = Objects.requireNonNull(calendarView);
        this.tasksView = Objects.requireNonNull(tasksView);
        this.notesView = Objects.requireNonNull(notesView);
        this.settingsView = Objects.requireNonNull(settingsView);
        this.genericView = Objects.requireNonNull(genericView);
        this.genericTitle = Objects.requireNonNull(genericTitle);
        this.genericIcon = Objects.requireNonNull(genericIcon);
        this.sidebarContainer = Objects.requireNonNull(sidebarContainer);
    }

    public void initialize() {
        bindManagedToVisible(homeView);
        bindManagedToVisible(dashboardView);
        bindManagedToVisible(contactsView);
        bindManagedToVisible(calendarView);
        bindManagedToVisible(tasksView);
        bindManagedToVisible(notesView);
        bindManagedToVisible(settingsView);
        bindManagedToVisible(genericView);
    }

    public void navigate(ActionEvent event) {
        Button button = (Button) event.getSource();
        updateActiveStyles(button);
        String id = button.getId();
        hideAll();
        if (id.contains("Home")) homeView.setVisible(true);
        else if (id.contains("Dashboard")) dashboardView.setVisible(true);
        else if (id.contains("Contacts")) contactsView.setVisible(true);
        else if (id.contains("Calendar")) calendarView.setVisible(true);
        else if (id.contains("Tasks")) tasksView.setVisible(true);
        else if (id.contains("Notes")) notesView.setVisible(true);
        else if (id.contains("Settings")) settingsView.setVisible(true);
        else showPlaceholder(id);
    }

    public void showContacts() {
        showView(contactsView, "Contacts");
    }

    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = Objects.requireNonNull(onNavigate);
    }

    public void showHome() { showView(homeView, "Home"); }
    public void showDashboard() { showView(dashboardView, "Dashboard"); }
    public void showTasks() { showView(tasksView, "Tasks"); }
    public void showSettings() { showView(settingsView, "Settings"); }

    public void showCalendar() {
        showView(calendarView, "Calendar");
    }

    public void showNotes() {
        showView(notesView, "Notes");
    }

    private void showView(Node view, String buttonIdPart) {
        hideAll();
        view.setVisible(true);
        Button selectedButton = sidebarContainer.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getId() != null && button.getId().contains(buttonIdPart))
                .findFirst()
                .orElse(null);
        updateActiveStyles(selectedButton);
    }

    private void bindManagedToVisible(Node view) {
        view.managedProperty().bind(view.visibleProperty());
    }

    private void hideAll() {
        homeView.setVisible(false);
        dashboardView.setVisible(false);
        contactsView.setVisible(false);
        calendarView.setVisible(false);
        tasksView.setVisible(false);
        notesView.setVisible(false);
        settingsView.setVisible(false);
        genericView.setVisible(false);
    }

    private void showPlaceholder(String id) {
        genericView.setVisible(true);
        setPlaceholder("Section", "fas-folder-open");
    }

    private void setPlaceholder(String title, String icon) {
        genericTitle.setText(title);
        genericIcon.setIconLiteral(icon);
    }

    private void updateActiveStyles(Button selected) {
        for (Node node : sidebarContainer.getChildren()) {
            if (node instanceof Button) node.getStyleClass().remove("sidebar-button-active");
        }
        if (selected != null && selected.getStyleClass().contains("sidebar-button")) {
            selected.getStyleClass().add("sidebar-button-active");
            onNavigate.accept(selected.getText());
        }
    }
}
