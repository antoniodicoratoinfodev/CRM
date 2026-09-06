package com.crm.controller;

import com.crm.service.ThemeService;
import com.crm.service.WorkspaceSearchService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Keyboard-accessible entry point for searching records and running workspace actions. */
public final class WorkspaceSearchController {
    public record Command(String title, String detail, String icon, Runnable action) { }
    private record Item(String title, String detail, String icon, String kind, Runnable action) { }

    private final ThemeService themes;
    private final Supplier<WorkspaceSearchService> indexSupplier;
    private final Consumer<WorkspaceSearchService.Result> openResult;
    private final List<Command> commands;
    private Dialog<Item> activeDialog;

    public WorkspaceSearchController(ThemeService themes, Supplier<WorkspaceSearchService> indexSupplier,
                                     Consumer<WorkspaceSearchService.Result> openResult, List<Command> commands) {
        this.themes = themes;
        this.indexSupplier = indexSupplier;
        this.openResult = openResult;
        this.commands = List.copyOf(commands);
    }

    public void show() {
        if (activeDialog != null) {
            activeDialog.getDialogPane().requestFocus();
            return;
        }
        WorkspaceSearchService index = indexSupplier.get();
        Dialog<Item> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Search VoidReach");
        themes.applyTo(dialog);
        dialog.getDialogPane().getStyleClass().add("command-dialog");
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setResultConverter(button -> null);

        TextField query = new TextField();
        query.setId("workspaceSearchInput");
        query.setPromptText("Search contacts, tasks, notes, or actions…");
        query.setAccessibleText("Search the entire workspace");
        query.getStyleClass().add("command-input");
        HBox.setHgrow(query, Priority.ALWAYS);
        FontIcon searchIcon = new FontIcon("fas-search");
        searchIcon.getStyleClass().add("command-search-icon");
        HBox search = new HBox(12, searchIcon, query);
        search.setAlignment(Pos.CENTER_LEFT);
        search.getStyleClass().add("command-search-row");

        Label section = new Label();
        section.getStyleClass().add("command-section");
        ListView<Item> results = new ListView<>();
        results.setId("workspaceSearchResults");
        results.setAccessibleText("Search results and quick actions");
        results.setPrefHeight(364);
        results.setFixedCellSize(60);
        results.getStyleClass().add("command-results");
        Label noResults = new Label("No matches. Try a name, company, or a word from a note.");
        noResults.setWrapText(true);
        noResults.getStyleClass().add("command-empty");
        results.setPlaceholder(noResults);
        results.setCellFactory(view -> new ListCell<>() {
            {
                setPrefWidth(0);
                setOnMouseClicked(event -> {
                    if (!isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                        activate(dialog, getItem());
                        event.consume();
                    }
                });
            }

            @Override protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
                setAccessibleText(null);
                if (empty || item == null) return;
                FontIcon icon = new FontIcon(item.icon());
                icon.getStyleClass().add("command-result-icon");
                StackPane iconBox = new StackPane(icon);
                iconBox.getStyleClass().add("command-icon-wrap");
                Label title = new Label(item.title());
                title.setMaxWidth(Double.MAX_VALUE);
                title.getStyleClass().add("command-result-title");
                Label detail = new Label(item.detail());
                detail.setMaxWidth(Double.MAX_VALUE);
                detail.getStyleClass().add("command-result-detail");
                VBox text = new VBox(3, title, detail);
                text.setMinWidth(0);
                HBox.setHgrow(text, Priority.ALWAYS);
                Label kind = new Label(item.kind());
                kind.setMinWidth(Region.USE_PREF_SIZE);
                kind.getStyleClass().add("command-result-kind");
                HBox row = new HBox(12, iconBox, text, kind);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMinWidth(0);
                row.prefWidthProperty().bind(widthProperty().subtract(22));
                row.maxWidthProperty().bind(widthProperty().subtract(22));
                setGraphic(row);
                setAccessibleText(item.kind() + ": " + item.title() + ". " + item.detail());
            }
        });

        Runnable refresh = () -> {
            String text = query.getText().trim();
            List<Item> items = new ArrayList<>();
            commands.stream().filter(command -> text.isEmpty()
                    || WorkspaceSearchService.matches(command.title(), text))
                    .forEach(command -> items.add(new Item(command.title(), command.detail(),
                            command.icon(), "Action", command.action())));
            List<WorkspaceSearchService.Result> matches = index.search(text, 31);
            matches.stream().limit(30).forEach(result -> items.add(new Item(result.title(), result.detail(),
                    icon(result.kind()), kind(result.kind()), () -> openResult.accept(result))));
            results.getItems().setAll(items);
            if (!items.isEmpty()) results.getSelectionModel().selectFirst();
            results.scrollTo(0);
            section.setText(text.isEmpty() ? "QUICK ACTIONS"
                    : matches.size() > 30 ? "30+ MATCHES · REFINE YOUR SEARCH"
                    : items.size() + (items.size() == 1 ? " RESULT" : " RESULTS"));
        };
        query.textProperty().addListener((observable, oldValue, newValue) -> refresh.run());
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getTarget() instanceof ButtonBase) return;
            if (event.getCode() == KeyCode.ENTER) {
                activate(dialog, results.getSelectionModel().getSelectedItem());
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.UP) {
                int direction = event.getCode() == KeyCode.DOWN ? 1 : -1;
                int selected = results.getSelectionModel().getSelectedIndex();
                int next = Math.max(0, Math.min(results.getItems().size() - 1, selected + direction));
                results.getSelectionModel().select(next);
                results.scrollTo(next);
                event.consume();
            }
        });

        Label keyboardHint = new Label("↑ ↓  Navigate     ↵  Open     Esc  Close");
        keyboardHint.getStyleClass().add("command-keyboard-hint");
        VBox content = new VBox(10, search, section, results, keyboardHint);
        content.getStyleClass().add("command-content");
        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> query.requestFocus());
        dialog.setOnHidden(event -> {
            if (activeDialog != dialog) return;
            activeDialog = null;
            Item item = dialog.getResult();
            if (item != null) Platform.runLater(item.action());
        });
        refresh.run();
        dialog.show();
    }

    private static void activate(Dialog<Item> dialog, Item item) {
        if (item == null || !dialog.isShowing()) return;
        // Updating a Dialog result closes it; a second close would dispatch the action twice.
        dialog.setResult(item);
    }

    private static String icon(WorkspaceSearchService.Kind kind) {
        return switch (kind) {
            case CONTACT -> "fas-user";
            case TASK -> "far-check-circle";
            case NOTE -> "far-file-alt";
        };
    }

    private static String kind(WorkspaceSearchService.Kind kind) {
        return switch (kind) {
            case CONTACT -> "Contact";
            case TASK -> "Task";
            case NOTE -> "Note";
        };
    }
}
