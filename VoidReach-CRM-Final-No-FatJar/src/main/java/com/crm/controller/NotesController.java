package com.crm.controller;

import com.crm.model.Note;
import com.crm.model.NoteFormat;
import com.crm.model.NoteFolder;
import com.crm.model.Task;
import com.crm.service.CodeSyntaxHighlighter;
import com.crm.service.ThemeService;
import com.crm.service.Typography;
import com.crm.service.ExternalLinks;
import com.crm.view.MarkdownPreviewView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns the notes library, full-size editor, ordering, Markdown preview, and task links. */
public final class NotesController {
    private static final DataFormat NOTE_ID = new DataFormat("application/x-voidreach-note-id");
    private static final DataFormat FOLDER_ID = new DataFormat("application/x-voidreach-folder-id");
    private static final FolderOption ROOT_FOLDER = new FolderOption(null, "All notes");
    private static final List<Double> FONT_SIZES = List.of(
            12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 28.0, 32.0, 36.0, 40.0, 48.0);
    private static final List<String> FONT_WEIGHTS = List.of("Regular", "Medium", "Semibold", "Bold");
    private final VBox libraryPane;
    private final VBox editorPane;
    private final TextField searchField;
    private final TilePane grid;
    private final Label emptyLabel;
    private final Label countLabel;
    private final TextField titleField;
    private final Label formatLabel;
    private final Button backButton;
    private final ScrollPane breadcrumbScroll;
    private final HBox breadcrumbItems;
    private final ComboBox<FolderOption> folderCombo;
    private final ComboBox<TaskOption> taskCombo;
    private final MenuButton openTaskButton;
    private final HBox markdownToolbar;
    private final ToggleButton previewToggle;
    private final ComboBox<String> fontFamilyCombo;
    private final ComboBox<Double> fontSizeCombo;
    private final ComboBox<String> fontWeightCombo;
    private final ToggleButton boldToggle;
    private final ToggleButton italicToggle;
    private final HBox previewSettingsBar;
    private final ComboBox<String> previewFontFamilyCombo;
    private final ComboBox<Double> previewFontSizeCombo;
    private final ColorPicker previewColorPicker;
    private final CodeArea contentArea;
    private final StackPane previewPane;
    private final Label editorStatus;
    private final ThemeService themeService;
    private final NoteActions actions;
    private final List<Note> notes = new ArrayList<>();
    private final List<NoteFolder> folders = new ArrayList<>();
    private Map<LocalDate, List<Task>> tasksByDate = Map.of();
    private Note currentNote;
    private java.util.function.Consumer<Note> noteOpened = note -> { };
    public void setOnNoteOpened(java.util.function.Consumer<Note> action) { noteOpened = action; }
    private String currentFolderId = "";
    private boolean updatingEditor;
    private final PauseTransition highlightDebounce = new PauseTransition(Duration.millis(45));
    private final PauseTransition linkedViewsDebounce = new PauseTransition(Duration.millis(90));

    public NotesController(VBox libraryPane, VBox editorPane, TextField searchField, TilePane grid,
                           Label emptyLabel, Label countLabel, TextField titleField, Label formatLabel,
                           Button backButton, ScrollPane breadcrumbScroll, HBox breadcrumbItems,
                           ComboBox<FolderOption> folderCombo,
                           ComboBox<TaskOption> taskCombo, MenuButton openTaskButton, HBox markdownToolbar,
                           ToggleButton previewToggle, ComboBox<String> fontFamilyCombo,
                           ComboBox<Double> fontSizeCombo, ComboBox<String> fontWeightCombo,
                           ToggleButton boldToggle, ToggleButton italicToggle,
                           HBox previewSettingsBar, ComboBox<String> previewFontFamilyCombo,
                           ComboBox<Double> previewFontSizeCombo, ColorPicker previewColorPicker,
                           CodeArea contentArea, StackPane previewPane,
                           Label editorStatus, ThemeService themeService,
                           NoteActions actions) {
        this.libraryPane = Objects.requireNonNull(libraryPane);
        this.editorPane = Objects.requireNonNull(editorPane);
        this.searchField = Objects.requireNonNull(searchField);
        this.grid = Objects.requireNonNull(grid);
        this.emptyLabel = Objects.requireNonNull(emptyLabel);
        this.countLabel = Objects.requireNonNull(countLabel);
        this.titleField = Objects.requireNonNull(titleField);
        this.formatLabel = Objects.requireNonNull(formatLabel);
        this.backButton = Objects.requireNonNull(backButton);
        this.breadcrumbScroll = Objects.requireNonNull(breadcrumbScroll);
        this.breadcrumbItems = Objects.requireNonNull(breadcrumbItems);
        this.folderCombo = Objects.requireNonNull(folderCombo);
        this.taskCombo = Objects.requireNonNull(taskCombo);
        this.openTaskButton = Objects.requireNonNull(openTaskButton);
        this.markdownToolbar = Objects.requireNonNull(markdownToolbar);
        this.previewToggle = Objects.requireNonNull(previewToggle);
        this.fontFamilyCombo = Objects.requireNonNull(fontFamilyCombo);
        this.fontSizeCombo = Objects.requireNonNull(fontSizeCombo);
        this.fontWeightCombo = Objects.requireNonNull(fontWeightCombo);
        this.boldToggle = Objects.requireNonNull(boldToggle);
        this.italicToggle = Objects.requireNonNull(italicToggle);
        this.previewSettingsBar = Objects.requireNonNull(previewSettingsBar);
        this.previewFontFamilyCombo = Objects.requireNonNull(previewFontFamilyCombo);
        this.previewFontSizeCombo = Objects.requireNonNull(previewFontSizeCombo);
        this.previewColorPicker = Objects.requireNonNull(previewColorPicker);
        this.contentArea = Objects.requireNonNull(contentArea);
        this.previewPane = Objects.requireNonNull(previewPane);
        this.editorStatus = Objects.requireNonNull(editorStatus);
        this.themeService = Objects.requireNonNull(themeService);
        this.actions = Objects.requireNonNull(actions);
    }

    public void initialize() {
        contentArea.setPlaceholder(new Label("Start writing…"));
        Typography.load();
        List<String> families = new ArrayList<>(Font.getFamilies());
        families.removeAll(List.of("System", Typography.UI, Typography.CODE));
        families.removeIf(family -> family.startsWith("Inter ") || family.startsWith("JetBrains Mono "));
        families.addAll(0, List.of("System", Typography.UI, Typography.CODE));
        fontFamilyCombo.setItems(FXCollections.observableArrayList(families));
        previewFontFamilyCombo.setItems(FXCollections.observableArrayList(families));
        var familyNames = new javafx.util.StringConverter<String>() {
            @Override public String toString(String family) { return "System".equals(family) ? "Default" : safe(family); }
            @Override public String fromString(String value) { return "Default".equals(value) ? "System" : value; }
        };
        fontFamilyCombo.setConverter(familyNames); previewFontFamilyCombo.setConverter(familyNames);
        fontFamilyCombo.setTooltip(new Tooltip("Default uses JetBrains Mono for Markdown source and Inter for plain text."));
        previewFontFamilyCombo.setTooltip(new Tooltip("Default uses the bundled Inter reading font. This does not change the editor."));
        fontSizeCombo.setItems(FXCollections.observableArrayList(FONT_SIZES));
        previewFontSizeCombo.setItems(FXCollections.observableArrayList(FONT_SIZES));
        var sizeNames = new javafx.util.StringConverter<Double>() {
            @Override public String toString(Double size) { return size == null ? "" : size.toString().replaceFirst("\\.0$", ""); }
            @Override public Double fromString(String value) { return Double.valueOf(value); }
        };
        fontSizeCombo.setConverter(sizeNames); previewFontSizeCombo.setConverter(sizeNames);
        fontWeightCombo.setItems(FXCollections.observableArrayList(FONT_WEIGHTS));
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> renderLibrary());
        titleField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setTitle(newValue);
            refreshLinkedViewsSoon();
            changed();
        });
        contentArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setContent(newValue);
            scheduleLiveHighlight();
            if (previewToggle.isSelected()) renderPreview();
            changed();
        });
        highlightDebounce.setOnFinished(event -> applySyntaxHighlighting());
        linkedViewsDebounce.setOnFinished(event -> actions.notePresentationChanged());
        taskCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            if (newValue == null || newValue.task() == null) return;
            currentNote.linkTask(newValue.task().getId());
            refreshTaskChoices();
            refreshLinkedViewsSoon();
            changed();
        });
        folderCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setFolderId(newValue == null || newValue.folder() == null
                    ? "" : newValue.folder().getId());
            changed();
        });
        fontFamilyCombo.valueProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        fontSizeCombo.valueProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        fontWeightCombo.valueProperty().addListener((observable, oldValue, newValue) -> weightSelectionChanged());
        boldToggle.selectedProperty().addListener((observable, oldValue, selected) -> boldSelectionChanged(selected));
        italicToggle.selectedProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        previewFontFamilyCombo.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged(false));
        previewFontSizeCombo.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged(false));
        previewColorPicker.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged(true));
        contentArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB && isMarkdown()) {
                contentArea.replaceSelection("    ");
                event.consume();
                return;
            }
            if (!(event.isControlDown() || event.isMetaDown())) return;
            if (event.getCode() == KeyCode.B && isMarkdown()) { wrapSelection("**", "**", "bold text"); event.consume(); }
            else if (event.getCode() == KeyCode.I && isMarkdown()) { wrapSelection("*", "*", "italic text"); event.consume(); }
        });
        showLibrary();
    }

    public void applyState(List<Note> source, List<NoteFolder> sourceFolders,
                           Map<LocalDate, List<Task>> tasks) {
        currentNote = null;
        currentFolderId = "";
        notes.clear();
        if (source != null) notes.addAll(source);
        folders.clear();
        if (sourceFolders != null) folders.addAll(sourceFolders);
        refreshTasks(tasks);
        showLibrary();
    }

    public void refreshTasks(Map<LocalDate, List<Task>> tasks) {
        tasksByDate = tasks == null ? Map.of() : tasks;
        if (currentNote != null) {
            boolean wasUpdating = updatingEditor;
            updatingEditor = true;
            refreshTaskChoices();
            updatingEditor = wasUpdating;
        }
        renderLibrary();
    }

    public List<Note> snapshot() { return new ArrayList<>(notes); }
    public Note currentNote() { return currentNote; }
    public void linkContact(String contactId) { if (currentNote != null) { currentNote.setContactId(contactId); changed(); } }
    public void createForContact(com.crm.model.Contact contact) {
        Note note = new Note("Meeting · " + contact.nameProperty().get(), NoteFormat.MARKDOWN);
        note.setContent(template("Meeting")); note.setContactId(contact.getId()); notes.add(note); changed(); actions.showNotes(); open(note);
    }
    private static String template(String type) {
        return switch (type) {
            case "Meeting" -> "# Meeting notes\n\n## Agenda\n- \n\n## Decisions\n- \n\n## Next steps\n- [ ] \n";
            case "Call" -> "# Call notes\n\n## Context\n\n## Conversation\n\n## Follow-up\n- [ ] \n";
            case "Project brief" -> "# Project brief\n\n## Goal\n\n## Scope\n\n## Deliverables\n- [ ] \n\n## Open questions\n";
            default -> "";
        };
    }
    public List<NoteFolder> foldersSnapshot() { return new ArrayList<>(folders); }

    public void refreshTheme() {
        if (currentNote == null) return;
        if (currentNote.getPreviewTextColor().isBlank()) {
            updatingEditor = true;
            previewColorPicker.setValue(Color.web(defaultPreviewColor()));
            updatingEditor = false;
        }
        if (previewToggle.isSelected()) renderPreview();
        applySyntaxHighlighting();
    }

    public List<Note> notesForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return List.of();
        return notes.stream().filter(note -> note.isLinkedToTask(taskId)).toList();
    }

    public void createNote() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New note");
        themeService.applyTo(dialog);
        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        TextField title = new TextField();
        title.setPromptText("Note title");
        ComboBox<NoteFormat> format = new ComboBox<>(FXCollections.observableArrayList(NoteFormat.values()));
        format.setValue(NoteFormat.MARKDOWN);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new javafx.geometry.Insets(18));
        form.add(new Label("Title:"), 0, 0);
        form.add(title, 1, 0);
        form.add(new Label("Format:"), 0, 1);
        form.add(format, 1, 1);
        ComboBox<String> templateChoice = new ComboBox<>(FXCollections.observableArrayList("Blank", "Meeting", "Call", "Project brief"));
        templateChoice.setValue("Blank"); form.add(new Label("Template:"), 0, 2); form.add(templateChoice, 1, 2);
        dialog.getDialogPane().setContent(form);
        if (dialog.showAndWait().filter(create::equals).isEmpty()) return;
        Note note = new Note(title.getText().isBlank() ? "Untitled note" : title.getText().trim(), format.getValue());
        note.setContent(template(templateChoice.getValue()));
        note.setFolderId(currentFolderId);
        notes.add(note);
        changed();
        open(note);
    }

    public void createFolder() {
        Optional<String> name = requestFolderName("New folder", "Create a folder", "Folder name", "Create", "");
        if (name.isEmpty()) return;
        if (folderNameExists(name.get(), null)) {
            showFolderNameConflict();
            return;
        }
        folders.add(new NoteFolder(java.util.UUID.randomUUID().toString(), name.get(), currentFolderId));
        changed();
    }

    public void openRoot() {
        currentFolderId = "";
        searchField.clear();
        updateFolderNavigation();
        renderLibrary();
    }

    public void navigateUp() {
        String parentId = findFolder(currentFolderId).map(NoteFolder::getParentFolderId).orElse("");
        navigateToFolder(parentId);
    }

    public void openById(String noteId) {
        notes.stream().filter(note -> note.getId().equals(noteId)).findFirst().ifPresent(note -> {
            actions.showNotes();
            open(note);
        });
    }

    public void closeEditor() {
        currentNote = null;
        showLibrary();
    }

    public void deleteCurrent() {
        if (currentNote == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete note");
        confirmation.setHeaderText("Delete “" + displayTitle(currentNote) + "”?");
        confirmation.setContentText("You can restore this note from History → Recently deleted.");
        themeService.applyTo(confirmation);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        notes.remove(currentNote);
        currentNote = null;
        actions.notePresentationChanged();
        changed();
        showLibrary();
    }

    public void openLinkedTask() {
        currentNote.getLinkedTaskIds().stream().map(this::findTask).flatMap(Optional::stream)
                .findFirst().ifPresent(selected -> actions.openTask(selected.date(), selected.task()));
    }

    public void togglePreview() {
        boolean preview = isMarkdown() && previewToggle.isSelected();
        previewToggle.setText(preview ? "Edit" : "Preview");
        previewToggle.setVisible(isMarkdown()); previewToggle.setManaged(isMarkdown());
        markdownToolbar.setVisible(isMarkdown() && !preview); markdownToolbar.setManaged(isMarkdown() && !preview);
        for (Node child : markdownToolbar.getChildren()) if (child instanceof ButtonBase && child != previewToggle) child.setDisable(preview);
        contentArea.setVisible(!preview);
        contentArea.setManaged(!preview);
        previewPane.setVisible(preview);
        previewPane.setManaged(preview);
        previewSettingsBar.setVisible(preview);
        previewSettingsBar.setManaged(preview);
        if (preview) { renderPreview(); readingView.focus(); }
        else contentArea.requestFocus();
    }

    public void markdownHeading() { prefixCurrentLine("## "); }
    public void markdownBold() { wrapSelection("**", "**", "bold text"); }
    public void markdownItalic() { wrapSelection("*", "*", "italic text"); }
    public void markdownLink() { wrapSelection("[", "](https://)", "link text"); }
    public void markdownCode() {
        String selection = contentArea.getSelectedText();
        if (selection.contains("\n") || selection.contains("\r")) {
            wrapSelection("```\n", "\n```", "code");
        } else {
            wrapSelection("`", "`", "code");
        }
    }
    public void markdownChecklist() { prefixCurrentLine("- [ ] "); }

    public void resetTypography() {
        if (currentNote == null) return;
        updatingEditor = true;
        fontFamilyCombo.setValue(Note.DEFAULT_FONT_FAMILY);
        fontSizeCombo.setValue(Note.DEFAULT_FONT_SIZE);
        fontWeightCombo.setValue(weightName(Note.DEFAULT_FONT_WEIGHT));
        boldToggle.setSelected(false);
        italicToggle.setSelected(false);
        updatingEditor = false;
        typographyChanged();
    }

    public void resetPreviewTypography() {
        if (currentNote == null) return;
        updatingEditor = true;
        previewFontFamilyCombo.setValue(Note.DEFAULT_PREVIEW_FONT_FAMILY);
        previewFontSizeCombo.setValue(Note.DEFAULT_PREVIEW_FONT_SIZE);
        previewColorPicker.setValue(Color.web(defaultPreviewColor()));
        currentNote.setPreviewFontFamily(Note.DEFAULT_PREVIEW_FONT_FAMILY);
        currentNote.setPreviewFontSize(Note.DEFAULT_PREVIEW_FONT_SIZE);
        currentNote.setPreviewTextColor("");
        updatingEditor = false;
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void open(Note note) {
        currentNote = note;
        updatingEditor = true;
        titleField.setText(note.getTitle());
        contentArea.replaceText(note.getContent());
        formatLabel.setText(note.getFormat().extension());
        ensureFontFamilyAvailable(note.getFontFamily());
        ensureFontSizeAvailable(note.getFontSize());
        fontFamilyCombo.setValue(note.getFontFamily());
        fontSizeCombo.setValue(note.getFontSize());
        fontWeightCombo.setValue(weightName(note.getFontWeight()));
        boldToggle.setSelected(note.getFontWeight() >= 700);
        italicToggle.setSelected(note.isItalic());
        ensurePreviewFontFamilyAvailable(note.getPreviewFontFamily());
        ensurePreviewFontSizeAvailable(note.getPreviewFontSize());
        previewFontFamilyCombo.setValue(note.getPreviewFontFamily());
        previewFontSizeCombo.setValue(note.getPreviewFontSize());
        previewColorPicker.setValue(Color.web(note.getPreviewTextColor().isBlank()
                ? defaultPreviewColor() : note.getPreviewTextColor()));
        markdownToolbar.setVisible(isMarkdown());
        markdownToolbar.setManaged(isMarkdown());
        previewToggle.setSelected(false);
        togglePreview();
        contentArea.setVisible(true);
        contentArea.setManaged(true);
        previewPane.setVisible(false);
        previewPane.setManaged(false);
        previewSettingsBar.setVisible(false);
        previewSettingsBar.setManaged(false);
        refreshFolderChoices();
        refreshTaskChoices();
        applyTypography();
        applySyntaxHighlighting();
        updatingEditor = false;
        libraryPane.setVisible(false);
        libraryPane.setManaged(false);
        editorPane.setVisible(true);
        editorPane.setManaged(true);
        noteOpened.accept(note);
        titleField.requestFocus();
    }

    private void showLibrary() {
        editorPane.setVisible(false);
        editorPane.setManaged(false);
        libraryPane.setVisible(true);
        libraryPane.setManaged(true);
        updateFolderNavigation();
        renderLibrary();
    }

    private void renderLibrary() {
        if (grid == null) return;
        grid.getChildren().clear();
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        List<NoteFolder> visibleFolders = query.isEmpty()
                ? folders.stream().filter(folder -> currentFolderId.equals(validParentId(folder))).toList()
                : List.of();
        visibleFolders.forEach(folder -> grid.getChildren().add(folderCard(folder)));
        List<Note> visible = notes.stream().filter(note -> {
            boolean matchesQuery = query.isEmpty()
                    || note.getTitle().toLowerCase(Locale.ROOT).contains(query)
                    || note.getContent().toLowerCase(Locale.ROOT).contains(query);
            if (!matchesQuery) return false;
            if (!query.isEmpty()) return true;
            return currentFolderId.isBlank() ? isRootNote(note) : currentFolderId.equals(note.getFolderId());
        }).toList();
        visible.forEach(note -> grid.getChildren().add(noteCard(note)));
        if (query.isEmpty() && currentFolderId.isBlank()) {
            long rootNotes = notes.stream().filter(this::isRootNote).count();
            long rootFolders = visibleFolders.size();
            countLabel.setText(rootNotes + (rootNotes == 1 ? " note" : " notes") + " · "
                    + rootFolders + (rootFolders == 1 ? " folder" : " folders"));
        } else {
            countLabel.setText(visible.size() + (visible.size() == 1 ? " note" : " notes")
                    + (query.isEmpty() ? " · " + visibleFolders.size()
                    + (visibleFolders.size() == 1 ? " folder" : " folders") : ""));
        }
        emptyLabel.setText(!query.isEmpty() ? "No notes match your search."
                : currentFolderId.isBlank() ? "There are no notes or folders yet. Create one to get started."
                : "This folder is empty. Create a note here or drag one into it.");
        emptyLabel.setVisible(visible.isEmpty() && visibleFolders.isEmpty());
    }

    private VBox folderCard(NoteFolder folder) {
        FontIcon icon = new FontIcon("fas-folder");
        icon.setIconSize(28);
        icon.getStyleClass().add("note-folder-icon");
        Label title = new Label(folder.getName());
        title.getStyleClass().add("note-folder-title");
        List<Note> folderNotes = notes.stream()
                .filter(note -> folder.getId().equals(note.getFolderId()))
                .toList();
        long noteCount = folderNotes.size();
        Label count = new Label(noteCount + (noteCount == 1 ? " note" : " notes"));
        count.getStyleClass().add("note-folder-count");
        long childFolderCount = folders.stream()
                .filter(candidate -> folder.getId().equals(validParentId(candidate))).count();
        if (childFolderCount > 0) {
            count.setText(count.getText() + " · " + childFolderCount
                    + (childFolderCount == 1 ? " folder" : " folders"));
        }

        VBox containedNotes = new VBox(4);
        containedNotes.getStyleClass().add("note-folder-files");
        if (folderNotes.isEmpty()) {
            Label empty = new Label("No notes inside");
            empty.getStyleClass().addAll("note-folder-file-title", "note-folder-file-empty");
            containedNotes.getChildren().add(empty);
        } else {
            folderNotes.forEach(note -> {
                Label noteTitle = new Label("•  " + displayTitle(note));
                noteTitle.setMaxWidth(Double.MAX_VALUE);
                noteTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
                noteTitle.setTooltip(new Tooltip(displayTitle(note)));
                noteTitle.getStyleClass().add("note-folder-file-title");
                containedNotes.getChildren().add(noteTitle);
            });
        }
        ScrollPane containedNotesScroll = new ScrollPane(containedNotes);
        containedNotesScroll.setFitToWidth(true);
        containedNotesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        containedNotesScroll.setVbarPolicy(folderNotes.size() > 3
                ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER);
        containedNotesScroll.setMaxHeight(66);
        containedNotesScroll.getStyleClass().add("note-folder-files-scroll");

        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(event -> renameFolder(folder));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(event -> deleteFolder(folder));
        MenuButton menu = new MenuButton("", null, rename, delete);
        menu.getStyleClass().addAll("icon-button", "note-folder-menu");
        menu.setGraphic(new FontIcon("fas-ellipsis-v"));
        menu.setOnMouseClicked(event -> event.consume());
        menu.setOnDragDetected(event -> event.consume());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        FontIcon drag = new FontIcon("fas-grip-vertical");
        drag.setIconSize(12);
        drag.getStyleClass().add("note-drag-handle");
        HBox header = new HBox(8, icon, spacer, drag, menu);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(9, header, title, count, containedNotesScroll);
        card.getStyleClass().add("note-folder-card");
        card.setPrefWidth(250);
        card.setOnMouseClicked(event -> openFolder(folder));
        card.setOnDragDetected(event -> {
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            var dragImage = card.snapshot(snapshotParameters, null);
            Dragboard board = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(FOLDER_ID, folder.getId());
            board.setContent(content);
            board.setDragView(dragImage,
                    Math.max(0, Math.min(event.getX(), dragImage.getWidth())),
                    Math.max(0, Math.min(event.getY(), dragImage.getHeight())));
            card.getStyleClass().add("note-folder-card-dragging");
            event.consume();
        });
        card.setOnDragDone(event -> {
            card.getStyleClass().remove("note-folder-card-dragging");
            clearBreadcrumbDropTarget();
        });
        card.setOnDragEntered(event -> {
            if (event.getGestureSource() != card && canAcceptDrop(event.getDragboard(), folder.getId())) {
                card.getStyleClass().add("note-folder-drop-target");
            }
        });
        card.setOnDragExited(event -> card.getStyleClass().remove("note-folder-drop-target"));
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && canAcceptDrop(event.getDragboard(), folder.getId())) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        card.setOnDragDropped(event -> {
            card.getStyleClass().remove("note-folder-drop-target");
            event.setDropCompleted(moveDroppedItem(event.getDragboard(), folder.getId()));
            event.consume();
        });
        return card;
    }

    private VBox noteCard(Note note) {
        FontIcon icon = new FontIcon(note.getFormat() == NoteFormat.MARKDOWN ? "fas-file-code" : "fas-file-alt");
        icon.setIconSize(15);
        icon.getStyleClass().add("note-card-icon");
        Label extension = new Label(note.getFormat().extension());
        extension.getStyleClass().add("note-card-format");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        FontIcon drag = new FontIcon("fas-grip-vertical");
        drag.setIconSize(12);
        drag.getStyleClass().add("note-drag-handle");
        HBox top = new HBox(8, icon, extension, spacer, drag);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(displayTitle(note));
        title.getStyleClass().add("note-card-title");
        Label excerpt = new Label(excerpt(note.getContent()));
        excerpt.setWrapText(true);
        excerpt.setMaxHeight(58);
        excerpt.getStyleClass().add("note-card-excerpt");
        Label link = new Label(linkedTaskCaption(note));
        link.getStyleClass().add("note-card-link");
        link.setVisible(!note.getLinkedTaskIds().isEmpty());
        link.setManaged(link.isVisible());
        VBox card = new VBox(10, top, title, excerpt, link);
        card.getStyleClass().add("note-card");
        card.setPrefWidth(250);
        card.setOnMouseClicked(event -> open(note));
        card.setOnDragDetected(event -> {
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            var dragImage = card.snapshot(snapshotParameters, null);
            Dragboard board = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(NOTE_ID, note.getId());
            board.setContent(content);
            board.setDragView(dragImage,
                    Math.max(0, Math.min(event.getX(), dragImage.getWidth())),
                    Math.max(0, Math.min(event.getY(), dragImage.getHeight())));
            card.getStyleClass().add("note-card-dragging");
            event.consume();
        });
        card.setOnDragDone(event -> {
            card.getStyleClass().remove("note-card-dragging");
            clearBreadcrumbDropTarget();
        });
        card.setOnDragEntered(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasContent(NOTE_ID)) {
                card.getStyleClass().add("note-card-drop-target");
            }
        });
        card.setOnDragExited(event -> card.getStyleClass().remove("note-card-drop-target"));
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasContent(NOTE_ID)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        card.setOnDragDropped(event -> {
            card.getStyleClass().remove("note-card-drop-target");
            Object draggedId = event.getDragboard().getContent(NOTE_ID);
            Note dragged = notes.stream().filter(candidate -> candidate.getId().equals(draggedId)).findFirst().orElse(null);
            if (dragged == null || dragged == note) { event.setDropCompleted(false); return; }
            dragged.setFolderId(note.getFolderId());
            int targetIndex = notes.indexOf(note);
            notes.remove(dragged);
            notes.add(Math.min(targetIndex, notes.size()), dragged);
            changed();
            event.setDropCompleted(true);
            event.consume();
        });
        return card;
    }

    private void openFolder(NoteFolder folder) {
        navigateToFolder(folder.getId());
    }

    private void updateFolderNavigation() {
        NoteFolder current = findFolder(currentFolderId).orElse(null);
        if (!currentFolderId.isBlank() && current == null) currentFolderId = "";
        boolean insideFolder = current != null;
        backButton.setVisible(insideFolder);
        backButton.setManaged(insideFolder);
        renderBreadcrumb();
    }

    private void renderBreadcrumb() {
        breadcrumbItems.getChildren().clear();
        addBreadcrumbSegment("Home", "");
        for (NoteFolder folder : folderTrail(currentFolderId)) {
            Label separator = new Label("›");
            separator.getStyleClass().add("note-breadcrumb-separator");
            breadcrumbItems.getChildren().add(separator);
            addBreadcrumbSegment(folder.getName(), folder.getId());
        }
        Platform.runLater(() -> breadcrumbScroll.setHvalue(1.0));
    }

    private void addBreadcrumbSegment(String text, String folderId) {
        Button segment = new Button(text);
        segment.getStyleClass().add("note-breadcrumb-segment");
        segment.setOnAction(event -> navigateToFolder(folderId));
        segment.setOnDragOver(event -> {
            if (canAcceptDrop(event.getDragboard(), folderId)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        segment.setOnDragEntered(event -> {
            if (canAcceptDrop(event.getDragboard(), folderId)) {
                showBreadcrumbDropTarget(segment);
            }
        });
        segment.setOnDragExited(event -> segment.getStyleClass().remove("note-breadcrumb-drop-target"));
        segment.setOnDragDropped(event -> {
            clearBreadcrumbDropTarget();
            event.setDropCompleted(moveDroppedItem(event.getDragboard(), folderId));
            event.consume();
        });
        breadcrumbItems.getChildren().add(segment);
    }

    private boolean canAcceptDrop(Dragboard board, String destinationFolderId) {
        if (board.hasContent(NOTE_ID)) {
            Object draggedId = board.getContent(NOTE_ID);
            return notes.stream().anyMatch(note -> note.getId().equals(draggedId));
        }
        if (!board.hasContent(FOLDER_ID)) return false;
        Object draggedId = board.getContent(FOLDER_ID);
        NoteFolder dragged = folders.stream()
                .filter(folder -> folder.getId().equals(draggedId)).findFirst().orElse(null);
        return canMoveFolder(dragged, destinationFolderId, folders);
    }

    private boolean moveDroppedItem(Dragboard board, String destinationFolderId) {
        if (board.hasContent(NOTE_ID)) {
            Object draggedId = board.getContent(NOTE_ID);
            Note dragged = notes.stream()
                    .filter(note -> note.getId().equals(draggedId)).findFirst().orElse(null);
            if (dragged == null) return false;
            dragged.setFolderId(destinationFolderId);
            changed();
            return true;
        }
        if (!board.hasContent(FOLDER_ID)) return false;
        Object draggedId = board.getContent(FOLDER_ID);
        NoteFolder dragged = folders.stream()
                .filter(folder -> folder.getId().equals(draggedId)).findFirst().orElse(null);
        if (!canMoveFolder(dragged, destinationFolderId, folders)) return false;
        dragged.setParentFolderId(destinationFolderId);
        changed();
        return true;
    }

    static boolean canMoveFolder(NoteFolder dragged, String destinationFolderId,
                                 List<NoteFolder> availableFolders) {
        if (dragged == null || availableFolders == null || !availableFolders.contains(dragged)) return false;
        String destination = normalizedFolderId(destinationFolderId);
        if (destination.equals(dragged.getId())) return false;
        if (!destination.isBlank() && findFolder(destination, availableFolders).isEmpty()) return false;
        if (resolvedParentId(dragged, availableFolders).equals(destination)) return false;

        String cursor = destination;
        int guard = availableFolders.size() + 1;
        while (!cursor.isBlank() && guard-- > 0) {
            if (cursor.equals(dragged.getId())) return false;
            NoteFolder ancestor = findFolder(cursor, availableFolders).orElse(null);
            if (ancestor == null) return false;
            cursor = resolvedParentId(ancestor, availableFolders);
        }
        if (!cursor.isBlank()) return false;

        return availableFolders.stream().noneMatch(candidate -> candidate != dragged
                && resolvedParentId(candidate, availableFolders).equals(destination)
                && candidate.getName().equalsIgnoreCase(dragged.getName()));
    }

    private static Optional<NoteFolder> findFolder(String folderId, List<NoteFolder> availableFolders) {
        return availableFolders.stream().filter(folder -> folder.getId().equals(folderId)).findFirst();
    }

    private static String resolvedParentId(NoteFolder folder, List<NoteFolder> availableFolders) {
        String parentId = normalizedFolderId(folder.getParentFolderId());
        return findFolder(parentId, availableFolders).isPresent() ? parentId : "";
    }

    private static String normalizedFolderId(String folderId) {
        return folderId == null ? "" : folderId.trim();
    }

    private void showBreadcrumbDropTarget(Button target) {
        clearBreadcrumbDropTarget();
        target.getStyleClass().add("note-breadcrumb-drop-target");
    }

    private void clearBreadcrumbDropTarget() {
        breadcrumbItems.getChildren().forEach(node ->
                node.getStyleClass().remove("note-breadcrumb-drop-target"));
    }

    private void navigateToFolder(String folderId) {
        currentFolderId = safe(folderId);
        searchField.clear();
        updateFolderNavigation();
        renderLibrary();
    }

    private void refreshFolderChoices() {
        List<FolderOption> options = new ArrayList<>();
        options.add(ROOT_FOLDER);
        folders.forEach(folder -> options.add(new FolderOption(folder, folderPath(folder))));
        folderCombo.setItems(FXCollections.observableArrayList(options));
        FolderOption selected = options.stream()
                .filter(option -> option.folder() != null
                        && option.folder().getId().equals(currentNote.getFolderId()))
                .findFirst().orElse(ROOT_FOLDER);
        folderCombo.setValue(selected);
    }

    private void renameFolder(NoteFolder folder) {
        Optional<String> name = requestFolderName("Rename folder", "Rename folder", "Folder name", "Save",
                folder.getName());
        if (name.isEmpty() || name.get().equals(folder.getName())) return;
        if (folderNameExists(name.get(), folder)) {
            showFolderNameConflict();
            return;
        }
        folder.setName(name.get());
        updateFolderNavigation();
        changed();
    }

    private void deleteFolder(NoteFolder folder) {
        long noteCount = notes.stream().filter(note -> folder.getId().equals(note.getFolderId())).count();
        long childFolderCount = folders.stream()
                .filter(candidate -> folder.getId().equals(validParentId(candidate))).count();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete folder");
        confirmation.setHeaderText("Delete “" + folder.getName() + "”?");
        confirmation.setContentText(noteCount == 0 && childFolderCount == 0
                ? "The empty folder will be deleted."
                : "Its notes and subfolders will be moved to the parent location.");
        themeService.applyTo(confirmation);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        String parentId = validParentId(folder);
        notes.stream().filter(note -> folder.getId().equals(note.getFolderId()))
                .forEach(note -> note.setFolderId(parentId));
        folders.stream().filter(candidate -> folder.getId().equals(candidate.getParentFolderId()))
                .forEach(candidate -> candidate.setParentFolderId(parentId));
        folders.remove(folder);
        if (folder.getId().equals(currentFolderId)) currentFolderId = parentId;
        updateFolderNavigation();
        changed();
    }

    private Optional<String> requestFolderName(String title, String header, String prompt,
                                               String actionText, String initialValue) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setHeaderText(header);
        themeService.applyTo(dialog);
        ButtonType action = new ButtonType(actionText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(action, ButtonType.CANCEL);
        TextField name = new TextField(initialValue);
        name.setPromptText(prompt);
        name.setPrefColumnCount(28);
        dialog.getDialogPane().setContent(name);
        Node actionButton = dialog.getDialogPane().lookupButton(action);
        actionButton.setDisable(name.getText().trim().isEmpty());
        name.textProperty().addListener((observable, oldValue, newValue) ->
                actionButton.setDisable(newValue == null || newValue.trim().isEmpty()));
        dialog.setOnShown(event -> {
            name.requestFocus();
            name.selectAll();
        });
        return dialog.showAndWait().filter(action::equals).map(ignored -> name.getText().trim());
    }

    private boolean folderNameExists(String name, NoteFolder excluded) {
        String parentId = excluded == null ? currentFolderId : validParentId(excluded);
        return folders.stream().anyMatch(folder -> folder != excluded
                && validParentId(folder).equals(parentId) && folder.getName().equalsIgnoreCase(name));
    }

    private void showFolderNameConflict() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Folder already exists");
        alert.setHeaderText("Choose a different folder name");
        alert.setContentText("Folder names must be unique.");
        themeService.applyTo(alert);
        alert.showAndWait();
    }

    private Optional<NoteFolder> findFolder(String folderId) {
        return folders.stream().filter(folder -> folder.getId().equals(folderId)).findFirst();
    }

    private boolean isRootNote(Note note) {
        return note.getFolderId().isBlank() || findFolder(note.getFolderId()).isEmpty();
    }

    private String validParentId(NoteFolder folder) {
        return findFolder(folder.getParentFolderId()).isPresent() ? folder.getParentFolderId() : "";
    }

    private List<NoteFolder> folderTrail(String folderId) {
        List<NoteFolder> reversed = new ArrayList<>();
        String cursor = safe(folderId);
        int guard = folders.size() + 1;
        while (!cursor.isBlank() && guard-- > 0) {
            NoteFolder folder = findFolder(cursor).orElse(null);
            if (folder == null || reversed.contains(folder)) break;
            reversed.add(folder);
            cursor = validParentId(folder);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private String folderPath(NoteFolder folder) {
        return "Home › " + folderTrail(folder.getId()).stream()
                .map(NoteFolder::getName)
                .reduce((left, right) -> left + " › " + right)
                .orElse(folder.getName());
    }

    private void refreshTaskChoices() {
        List<TaskOption> options = new ArrayList<>();
        tasksByDate.forEach((date, tasks) -> tasks.stream()
                .filter(task -> !currentNote.isLinkedToTask(task.getId()))
                .forEach(task -> options.add(new TaskOption(date, task))));
        options.sort(Comparator.comparing(TaskOption::date)
                .thenComparingInt(option -> option.task().getStartMin()));
        taskCombo.setItems(FXCollections.observableArrayList(options));
        taskCombo.setValue(null);
        updateOpenTaskButton();
    }

    private void updateOpenTaskButton() {
        List<TaskOption> linked = currentNote.getLinkedTaskIds().stream()
                .map(this::findTask).flatMap(Optional::stream).toList();
        openTaskButton.getItems().clear();
        linked.forEach(option -> {
            MenuItem open = new MenuItem("Open · " + option);
            open.setOnAction(event -> actions.openTask(option.date(), option.task()));
            MenuItem unlink = new MenuItem("Unlink · " + option.task().getTitle());
            unlink.setOnAction(event -> {
                currentNote.unlinkTask(option.task().getId());
                refreshTaskChoices();
                refreshLinkedViewsSoon();
                changed();
            });
            openTaskButton.getItems().addAll(open, unlink, new SeparatorMenuItem());
        });
        if (!openTaskButton.getItems().isEmpty()) openTaskButton.getItems().removeLast();
        boolean available = !linked.isEmpty();
        openTaskButton.setText(linked.size() + (linked.size() == 1 ? " linked task" : " linked tasks"));
        openTaskButton.setVisible(available);
        openTaskButton.setManaged(available);
    }

    private void scheduleLiveHighlight() {
        highlightDebounce.playFromStart();
    }

    private void refreshLinkedViewsSoon() {
        linkedViewsDebounce.playFromStart();
    }

    private void applySyntaxHighlighting() {
        if (currentNote == null) return;
        String text = contentArea.getText();
        if (text.isEmpty()) return;
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        if (!isMarkdown()) {
            spans.add(Collections.emptyList(), text.length());
            contentArea.setStyleSpans(0, spans.create());
            return;
        }
        String[] lines = text.split("\\n", -1);
        boolean fencedCode = false;
        boolean multilineInlineCode = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            boolean fence = line.stripLeading().startsWith("```");
            if (fence) {
                addSpan(spans, "syntax-delimiter", line.length());
                fencedCode = !fencedCode;
            } else if (fencedCode) {
                addCodeSpans(spans, line);
            } else {
                multilineInlineCode = addMarkdownSpans(spans, line, multilineInlineCode);
            }
            if (index < lines.length - 1) addSpan(spans, "markdown-source-text", 1);
        }
        contentArea.setStyleSpans(0, spans.create());
    }

    private boolean addMarkdownSpans(StyleSpansBuilder<Collection<String>> spans,
                                     String line, boolean insideCode) {
        int cursor = 0;
        while (cursor < line.length()) {
            if (insideCode) {
                int end = line.indexOf('`', cursor);
                if (end < 0) {
                    addCodeSpans(spans, line.substring(cursor));
                    return true;
                }
                addCodeSpans(spans, line.substring(cursor, end));
                addSpan(spans, "syntax-delimiter", 1);
                insideCode = false;
                cursor = end + 1;
                continue;
            }
            int start = line.indexOf('`', cursor);
            if (start < 0) {
                addSpan(spans, "markdown-source-text", line.length() - cursor);
                return false;
            }
            if (start > cursor) addSpan(spans, "markdown-source-text", start - cursor);
            addSpan(spans, "syntax-delimiter", 1);
            insideCode = true;
            cursor = start + 1;
        }
        return insideCode;
    }

    private void addCodeSpans(StyleSpansBuilder<Collection<String>> spans, String code) {
        for (CodeSyntaxHighlighter.Token token : CodeSyntaxHighlighter.highlight(code)) {
            addSpan(spans, "syntax-" + token.kind().name().toLowerCase(Locale.ROOT), token.text().length());
        }
    }

    private void addSpan(StyleSpansBuilder<Collection<String>> spans, String styleClass, int length) {
        if (length > 0) spans.add(List.of(styleClass), length);
    }

    private MarkdownPreviewView readingView;

    private void renderPreview() {
        if (currentNote == null) return;
        if (readingView == null) {
            readingView = new MarkdownPreviewView(this::openPreviewLink);
            previewPane.getChildren().setAll(readingView);
        }
        readingView.show(currentNote, themeService.activeTheme());
    }

    private void openPreviewLink(String address) {
        if (address.startsWith("voidreach-note:")) {
            String title;
            try { title = java.net.URLDecoder.decode(address.substring("voidreach-note:".length()), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalid) { editorStatus.setText("This note link has an invalid address."); return; }
            List<Note> matches = notes.stream().filter(note -> note.getTitle().equalsIgnoreCase(title)).toList();
            if (matches.size() == 1) open(matches.getFirst());
            else if (matches.isEmpty()) editorStatus.setText("Note not found: " + title);
            else editorStatus.setText("Several notes share this title. Use workspace search to choose one.");
            return;
        }
        try { ExternalLinks.open(address); }
        catch (RuntimeException failure) { editorStatus.setText("This link could not be opened. Check its address in the editor."); }
    }

    private void wrapSelection(String before, String after, String placeholder) {
        if (!isMarkdown()) return;
        int start = contentArea.getSelection().getStart();
        int end = contentArea.getSelection().getEnd();
        String selected = contentArea.getSelectedText();
        String body = selected.isEmpty() ? placeholder : selected;
        contentArea.replaceText(start, end, before + body + after);
        contentArea.selectRange(start + before.length(), start + before.length() + body.length());
        contentArea.requestFocus();
    }

    private void prefixCurrentLine(String prefix) {
        if (!isMarkdown()) return;
        int caret = contentArea.getCaretPosition();
        int lineStart = contentArea.getText().lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        contentArea.insertText(lineStart, prefix);
        contentArea.moveTo(caret + prefix.length());
        contentArea.requestFocus();
    }

    private void typographyChanged() {
        if (updatingEditor || currentNote == null || fontFamilyCombo.getValue() == null
                || fontSizeCombo.getValue() == null || fontWeightCombo.getValue() == null) return;
        currentNote.setFontFamily(fontFamilyCombo.getValue());
        currentNote.setFontSize(fontSizeCombo.getValue());
        currentNote.setFontWeight(weightValue(fontWeightCombo.getValue()));
        currentNote.setItalic(italicToggle.isSelected());
        applyTypography();
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void previewTypographyChanged(boolean colorChanged) {
        if (updatingEditor || currentNote == null || previewFontFamilyCombo.getValue() == null
                || previewFontSizeCombo.getValue() == null || previewColorPicker.getValue() == null) return;
        currentNote.setPreviewFontFamily(previewFontFamilyCombo.getValue());
        currentNote.setPreviewFontSize(previewFontSizeCombo.getValue());
        if (colorChanged) currentNote.setPreviewTextColor(toHex(previewColorPicker.getValue()));
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void weightSelectionChanged() {
        if (updatingEditor || fontWeightCombo.getValue() == null) return;
        updatingEditor = true;
        boldToggle.setSelected(weightValue(fontWeightCombo.getValue()) >= 700);
        updatingEditor = false;
        typographyChanged();
    }

    private void boldSelectionChanged(boolean selected) {
        if (updatingEditor || currentNote == null) return;
        updatingEditor = true;
        fontWeightCombo.setValue(selected ? "Bold" : "Regular");
        updatingEditor = false;
        typographyChanged();
    }

    private void applyTypography() {
        if (currentNote == null) return;
        contentArea.setStyle(typographyStyle(1.0, currentNote.getFontWeight(),
                currentNote.isItalic(), currentNote.getFontFamily()));
        applySyntaxHighlighting();
    }

    private String typographyStyle(double sizeMultiplier, int weight, boolean italic, String family) {
        return fontStyle(currentNote.getFontSize(), sizeMultiplier, weight, italic, family);
    }

    private String fontStyle(double baseSize, double sizeMultiplier, int weight, boolean italic, String family) {
        double size = Math.round(baseSize * sizeMultiplier * 10.0) / 10.0;
        return "-fx-font-family: \"" + cssFont(Typography.editorFamily(family, isMarkdown(), weight)) + "\";"
                + " -fx-font-size: " + size + "px;"
                + " -fx-font-weight: " + weight + ";"
                + " -fx-font-style: " + (italic ? "italic" : "normal") + ";";
    }

    private String cssFont(String family) { return safe(family).replace("\\", "").replace("\"", ""); }

    private void ensureFontFamilyAvailable(String family) {
        if (!fontFamilyCombo.getItems().contains(family)) fontFamilyCombo.getItems().add(family);
    }

    private void ensureFontSizeAvailable(double size) {
        if (!fontSizeCombo.getItems().contains(size)) {
            fontSizeCombo.getItems().add(size);
            fontSizeCombo.getItems().sort(Double::compareTo);
        }
    }

    private void ensurePreviewFontFamilyAvailable(String family) {
        if (!previewFontFamilyCombo.getItems().contains(family)) previewFontFamilyCombo.getItems().add(family);
    }

    private void ensurePreviewFontSizeAvailable(double size) {
        if (!previewFontSizeCombo.getItems().contains(size)) {
            previewFontSizeCombo.getItems().add(size);
            previewFontSizeCombo.getItems().sort(Double::compareTo);
        }
    }

    private String defaultPreviewColor() {
        return switch (themeService.activeTheme()) {
            case LIGHT -> "#202939";
            case DARK -> "#E7EBF2";
            case BLUE_GRAY -> "#E6EDF8";
            case GRAY_BLUE -> "#E6ECF2";
        };
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }

    private int weightValue(String name) {
        return switch (name) {
            case "Medium" -> 500;
            case "Semibold" -> 600;
            case "Bold" -> 700;
            default -> 400;
        };
    }

    private String weightName(int weight) {
        if (weight >= 700) return "Bold";
        if (weight >= 600) return "Semibold";
        if (weight >= 500) return "Medium";
        return "Regular";
    }

    private void changed() {
        editorStatus.setText("Unsaved changes");
        actions.dataChanged();
        if (libraryPane.isVisible()) renderLibrary();
    }

    private boolean isMarkdown() { return currentNote != null && currentNote.getFormat() == NoteFormat.MARKDOWN; }
    private String displayTitle(Note note) { return note.getTitle().isBlank() ? "Untitled note" : note.getTitle(); }
    private String excerpt(String content) {
        String plain = safe(content).replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[`*_>\\[\\]]", "").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) return "Empty note";
        return plain.length() > 135 ? plain.substring(0, 132) + "…" : plain;
    }

    private String linkedTaskCaption(Note note) {
        List<String> titles = note.getLinkedTaskIds().stream().map(this::findTask).flatMap(Optional::stream)
                .map(option -> option.task().getTitle()).toList();
        if (titles.isEmpty()) return "Linked tasks unavailable";
        return "Linked to " + titles.size() + (titles.size() == 1 ? " task: " : " tasks: ")
                + String.join(", ", titles);
    }

    private Optional<TaskOption> findTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        return tasksByDate.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(task -> new TaskOption(entry.getKey(), task)))
                .filter(option -> taskId.equals(option.task().getId())).findFirst();
    }

    private String safe(String value) { return value == null ? "" : value; }

    public record TaskOption(LocalDate date, Task task) {
        @Override public String toString() {
            return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) + " · " + task.getTitle();
        }
    }

    public record FolderOption(NoteFolder folder, String displayName) {
        @Override public String toString() {
            return displayName;
        }
    }

    public interface NoteActions {
        void dataChanged();
        void notePresentationChanged();
        void showNotes();
        void openTask(LocalDate date, Task task);
    }
}
