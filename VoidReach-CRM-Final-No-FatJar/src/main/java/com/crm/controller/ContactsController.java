package com.crm.controller;

import com.crm.model.Contact;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns contact presentation, filtering, pagination, editing, selection, and clipboard behavior. */
public final class ContactsController {
    /** Combo-box label meaning "no tag": stored on the contact as an empty string. */
    private static final String TAG_EMPTY = "Empty";
    private final TableView<Contact> table;
    private final TableColumn<Contact, String> nameColumn;
    private final TableColumn<Contact, String> companyColumn;
    private final TableColumn<Contact, String> titleColumn;
    private final TableColumn<Contact, String> emailColumn;
    private final TableColumn<Contact, String> phoneColumn;
    private final TableColumn<Contact, String> lastInteractionColumn;
    private final TableColumn<Contact, String> tagsColumn;
    private final TableColumn<Contact, String> descriptionColumn;
    private final TableColumn<Contact, Boolean> selectColumn;
    private final TextField searchField;
    private final ComboBox<String> rowsPerPageCombo;
    private final Pagination pagination;
    private final Label paginationInfoLabel;
    private final Button selectContactsButton;
    private final MenuButton sortMenu;
    private final ToggleButton inlineEditToggle;
    private final Button addFieldButton;
    private final ThemeService themeService;
    private final Runnable dataChanged;
    private java.util.function.Consumer<Contact> openDetails = this::showContactDialog;
    public void setOpenDetails(java.util.function.Consumer<Contact> action) { openDetails = action; }
    public void editRecord(Contact contact) { showContactDialog(contact); }
    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private final FilteredList<Contact> filteredContacts = new FilteredList<>(contacts, contact -> true);
    private final SortedList<Contact> sortedContacts = new SortedList<>(filteredContacts);
    private final Set<Contact> checkedContacts = new HashSet<>();
    private final Map<TableColumn<Contact, String>, StringProperty> columnTitles = new LinkedHashMap<>();
    private final Map<TableColumn<Contact, String>, StringProperty> customColumns = new LinkedHashMap<>();
    private final Map<TableColumn<Contact, String>, Menu> sortMenuEntries = new LinkedHashMap<>();
    private boolean selectionMode;

    public ContactsController(TableView<Contact> table,
                              TableColumn<Contact, String> nameColumn,
                              TableColumn<Contact, String> companyColumn,
                              TableColumn<Contact, String> titleColumn,
                              TableColumn<Contact, String> emailColumn,
                              TableColumn<Contact, String> phoneColumn,
                              TableColumn<Contact, String> lastInteractionColumn,
                              TableColumn<Contact, String> tagsColumn,
                              TableColumn<Contact, String> descriptionColumn,
                              TableColumn<Contact, Boolean> selectColumn,
                              TextField searchField, ComboBox<String> rowsPerPageCombo,
                              Pagination pagination, Label paginationInfoLabel,
                              Button selectContactsButton, MenuButton sortMenu, ToggleButton inlineEditToggle,
                              Button addFieldButton, ThemeService themeService, Runnable dataChanged) {
        this.table = Objects.requireNonNull(table);
        this.nameColumn = Objects.requireNonNull(nameColumn);
        this.companyColumn = Objects.requireNonNull(companyColumn);
        this.titleColumn = Objects.requireNonNull(titleColumn);
        this.emailColumn = Objects.requireNonNull(emailColumn);
        this.phoneColumn = Objects.requireNonNull(phoneColumn);
        this.lastInteractionColumn = Objects.requireNonNull(lastInteractionColumn);
        this.tagsColumn = Objects.requireNonNull(tagsColumn);
        this.descriptionColumn = Objects.requireNonNull(descriptionColumn);
        this.selectColumn = Objects.requireNonNull(selectColumn);
        this.searchField = Objects.requireNonNull(searchField);
        this.rowsPerPageCombo = Objects.requireNonNull(rowsPerPageCombo);
        this.pagination = Objects.requireNonNull(pagination);
        this.paginationInfoLabel = Objects.requireNonNull(paginationInfoLabel);
        this.selectContactsButton = Objects.requireNonNull(selectContactsButton);
        this.sortMenu = Objects.requireNonNull(sortMenu);
        this.inlineEditToggle = Objects.requireNonNull(inlineEditToggle);
        this.addFieldButton = Objects.requireNonNull(addFieldButton);
        this.themeService = Objects.requireNonNull(themeService);
        this.dataChanged = Objects.requireNonNull(dataChanged);
    }

    public void initialize() {
        setupColumns();
        setupEditableHeaders();
        setupSortMenu();
        setupInlineEditing();
        addFieldButton.setOnAction(event -> promptNewCustomField());
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setRowFactory(ignored -> {
            TableRow<Contact> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                boolean openRequest = event.getButton() == MouseButton.SECONDARY
                        || !inlineEditMode() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 1;
                if (!selectionMode && openRequest && !row.isEmpty()) {
                    openDetails.accept(row.getItem());
                    event.consume();
                }
            });
            return row;
        });
        rowsPerPageCombo.setItems(FXCollections.observableArrayList("15", "25", "50", "100", "All"));
        rowsPerPageCombo.setValue("15");
        rowsPerPageCombo.valueProperty().addListener((observable, oldValue, newValue) -> updatePagination());
        pagination.currentPageIndexProperty().addListener((observable, oldValue, newValue) -> updateTablePage());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterContacts(newValue);
            updatePagination();
        });
        table.setOnKeyPressed(this::handleTableKeyPressed);
        updatePagination();
    }

    public void setContacts(List<Contact> values) {
        contacts.setAll(values == null ? List.of() : values);
        checkedContacts.clear();
        updatePagination();
    }

    public List<Contact> snapshot() {
        return new ArrayList<>(contacts);
    }

    public void requestInitialFocus() {
        javafx.application.Platform.runLater(searchField::requestFocus);
    }

    public void addContact() {
        if (inlineEditMode()) addContactInline();
        else showContactDialog(null);
    }

    public void openById(String contactId) {
        contacts.stream().filter(contact -> contact.getId().equals(contactId))
                .findFirst().ifPresent(openDetails);
    }

    /** Quick-edit variant of "New contact": inserts an empty row and starts editing its name in place. */
    private void addContactInline() {
        Contact contact = new Contact("", "", "", "", "", "Just now", "", "");
        searchField.clear();
        contacts.add(0, contact);
        pagination.setCurrentPageIndex(0);
        updatePagination();
        dataChanged.run();
        int row = table.getItems().indexOf(contact);
        if (row < 0) return;
        table.getSelectionModel().clearAndSelect(row);
        table.scrollTo(row);
        javafx.application.Platform.runLater(() -> table.edit(row, nameColumn));
    }

    public void toggleSelection() {
        selectionMode = !selectionMode;
        selectColumn.setVisible(selectionMode);
        selectContactsButton.setText(selectionMode ? "Done" : "Select");
        table.getSelectionModel().setSelectionMode(selectionMode ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        if (!selectionMode) {
            checkedContacts.clear();
            table.getSelectionModel().clearSelection();
        }
        table.refresh();
    }

    public void deleteSelectedContacts() {
        List<Contact> selected = selectedContacts();
        if (selected.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Contact");
        themeService.applyTo(alert);
        alert.setHeaderText(selected.size() == 1
                ? "Delete " + selected.getFirst().nameProperty().get() + "?"
                : "Delete " + selected.size() + " contacts?");
        alert.setContentText("You can restore these contacts from History → Recently deleted.");
        if (alert.showAndWait().filter(result -> result == ButtonType.OK).isPresent()) {
            contacts.removeAll(selected);
            checkedContacts.removeAll(selected);
            table.getSelectionModel().clearSelection();
            updatePagination();
            dataChanged.run();
        }
    }

    private void setupColumns() {
        selectColumn.setCellValueFactory(data -> new SimpleBooleanProperty(checkedContacts.contains(data.getValue())));
        selectColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.getStyleClass().add("contact-select-check");
                checkBox.setOnAction(event -> {
                    Contact contact = getTableRow() == null ? null : getTableRow().getItem();
                    if (contact == null) return;
                    if (checkBox.isSelected()) checkedContacts.add(contact);
                    else checkedContacts.remove(contact);
                    table.refresh();
                });
            }
            @Override protected void updateItem(Boolean selected, boolean empty) {
                super.updateItem(selected, empty);
                if (empty || !selectionMode) setGraphic(null);
                else {
                    checkBox.setSelected(Boolean.TRUE.equals(selected));
                    setGraphic(checkBox);
                }
            }
        });
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        companyColumn.setCellValueFactory(new PropertyValueFactory<>("company"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        lastInteractionColumn.setCellValueFactory(new PropertyValueFactory<>("lastInteraction"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        Comparator<String> textComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        nameColumn.setComparator(textComparator);
        companyColumn.setComparator(textComparator);
        titleColumn.setComparator(textComparator);
        emailColumn.setComparator(textComparator);
        phoneColumn.setComparator(textComparator);
        lastInteractionColumn.setComparator(textComparator);
        tagsColumn.setComparator(textComparator);
        descriptionColumn.setComparator(textComparator);
        tagsColumn.setCellFactory(column -> new InlineTagCell());
    }

    private static String tagStorageValue(String selection) {
        return selection == null || TAG_EMPTY.equals(selection) ? "" : selection;
    }

    private static Label tagChip(String item) {
        Label tag = new Label(item);
        tag.getStyleClass().add("tag");
        if (item.equalsIgnoreCase("Client")) tag.getStyleClass().add("tag-client");
        else if (item.equalsIgnoreCase("Tech")) tag.getStyleClass().add("tag-tech");
        else if (item.equalsIgnoreCase("Follow-up")) tag.getStyleClass().add("tag-followup");
        return tag;
    }

    private boolean inlineEditMode() {
        return inlineEditToggle.isSelected();
    }

    private void setupInlineEditing() {
        table.setEditable(inlineEditToggle.isSelected());
        inlineEditToggle.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            table.setEditable(isSelected);
            if (!isSelected) table.edit(-1, null);
            dataChanged.run();
        });

        nameColumn.setCellFactory(column -> new InlineTextCell());
        companyColumn.setCellFactory(column -> new InlineTextCell());
        titleColumn.setCellFactory(column -> new InlineTextCell());
        emailColumn.setCellFactory(column -> new InlineTextCell());
        phoneColumn.setCellFactory(column -> new InlineTextCell());
        lastInteractionColumn.setCellFactory(column -> new InlineTextCell());
        descriptionColumn.setCellFactory(column -> new InlineDescriptionCell());

        nameColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setName));
        companyColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setCompany));
        titleColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setTitle));
        emailColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setEmail));
        phoneColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setPhone));
        lastInteractionColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setLastInteraction));
        tagsColumn.setOnEditCommit(event -> commitInlineEdit(event, Contact::setTags));
        descriptionColumn.setOnEditCommit(event -> commitDescriptionEdit(event));
    }

    private void commitDescriptionEdit(TableColumn.CellEditEvent<Contact, String> event) {
        Contact contact = event.getRowValue();
        if (contact == null) return;
        contact.setDescription(event.getNewValue() == null ? "" : event.getNewValue());
        dataChanged.run();
    }

    private void commitInlineEdit(TableColumn.CellEditEvent<Contact, String> event,
                                  java.util.function.BiConsumer<Contact, String> setter) {
        Contact contact = event.getRowValue();
        if (contact == null) return;
        setter.accept(contact, event.getNewValue() == null ? "" : event.getNewValue().trim());
        dataChanged.run();
    }

    /** Text cell that switches to an in-place editor on a single click while quick-edit mode is on. */
    private final class InlineTextCell extends TableCell<Contact, String> {
        private final TextField editor = new TextField();

        private InlineTextCell() {
            editor.getStyleClass().add("inline-cell-editor");
            editor.setOnAction(event -> commitEdit(editor.getText()));
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
            editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused && isEditing()) commitEdit(editor.getText());
            });
            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || !inlineEditMode() || isEmpty()) return;
                if (!isEditing()) getTableView().edit(getIndex(), getTableColumn());
                event.consume();
            });
        }

        @Override public void startEdit() {
            if (!inlineEditMode()) return;
            super.startEdit();
            if (!isEditing()) return;
            editor.setText(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.requestFocus();
            editor.selectAll();
        }

        @Override public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            setText(getItem());
        }

        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                setText(null);
                setGraphic(editor);
            } else {
                setGraphic(null);
                setText(item);
            }
        }
    }

    /**
     * Description cell: shows a single-line preview with ellipsis, exposes the full text through a
     * wrapping tooltip, and edits in place with a multi-line area while quick-edit mode is on.
     */
    private final class InlineDescriptionCell extends TableCell<Contact, String> {
        private static final int TOOLTIP_MAX_CHARS = 1200;
        private final TextArea editor = new TextArea();
        private final Tooltip fullText = new Tooltip();

        private InlineDescriptionCell() {
            editor.getStyleClass().add("inline-cell-area");
            editor.setWrapText(true);
            editor.setPrefRowCount(3);
            editor.setMinHeight(72);
            editor.setPrefHeight(72);
            editor.setMaxHeight(72);
            fullText.setWrapText(true);
            fullText.setMaxWidth(430);
            fullText.setShowDuration(javafx.util.Duration.INDEFINITE);
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                } else if (event.getCode() == KeyCode.ENTER && (event.isControlDown() || event.isMetaDown())) {
                    commitEdit(editor.getText());
                    event.consume();
                }
            });
            editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused && isEditing()) commitEdit(editor.getText());
            });
            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || !inlineEditMode() || isEmpty()) return;
                if (!isEditing()) getTableView().edit(getIndex(), getTableColumn());
                event.consume();
            });
        }

        @Override public void startEdit() {
            if (!inlineEditMode()) return;
            super.startEdit();
            if (!isEditing()) return;
            editor.setText(getItem() == null ? "" : getItem());
            setText(null);
            setTooltip(null);
            setGraphic(editor);
            editor.requestFocus();
            editor.positionCaret(editor.getText().length());
        }

        @Override public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            showPreview(getItem());
        }

        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else if (isEditing()) {
                setText(null);
                setTooltip(null);
                setGraphic(editor);
            } else {
                setGraphic(null);
                showPreview(item);
            }
        }

        private void showPreview(String item) {
            if (item == null || item.isBlank()) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(item.strip().replaceAll("\\s+", " "));
            fullText.setText(item.length() > TOOLTIP_MAX_CHARS ? item.substring(0, TOOLTIP_MAX_CHARS) + "…" : item);
            setTooltip(fullText);
        }
    }

    /** Tag cell that renders the usual chip and swaps to a combo box while quick-edit mode is on. */
    private final class InlineTagCell extends TableCell<Contact, String> {
        private final ComboBox<String> editor =
                new ComboBox<>(FXCollections.observableArrayList(TAG_EMPTY, "Client", "Tech", "Follow-up"));

        private InlineTagCell() {
            editor.getStyleClass().add("inline-cell-editor");
            editor.setMaxWidth(Double.MAX_VALUE);
            editor.setOnAction(event -> {
                if (isEditing()) commitEdit(tagStorageValue(editor.getValue()));
            });
            editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                    event.consume();
                }
            });
            setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || !inlineEditMode() || isEmpty()) return;
                if (!isEditing()) {
                    getTableView().edit(getIndex(), getTableColumn());
                    editor.show();
                }
                event.consume();
            });
        }

        @Override public void startEdit() {
            if (!inlineEditMode()) return;
            super.startEdit();
            if (!isEditing()) return;
            editor.setValue(getItem() == null || getItem().isBlank() ? TAG_EMPTY : getItem());
            setGraphic(editor);
        }

        @Override public void cancelEdit() {
            super.cancelEdit();
            showChip(getItem());
        }

        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) setGraphic(null);
            else if (isEditing()) setGraphic(editor);
            else showChip(item);
        }

        private void showChip(String item) {
            setGraphic(item == null || item.isBlank() ? null : tagChip(item));
        }
    }

    private void setupEditableHeaders() {
        configureEditableHeader(nameColumn, "Name");
        configureEditableHeader(companyColumn, "Company");
        configureEditableHeader(titleColumn, "Job title");
        configureEditableHeader(emailColumn, "Email");
        configureEditableHeader(phoneColumn, "Phone");
        configureEditableHeader(lastInteractionColumn, "Last interaction");
        configureEditableHeader(tagsColumn, "Tag");
        configureEditableHeader(descriptionColumn, "Description");
    }

    private void configureEditableHeader(TableColumn<Contact, String> column, String initialTitle) {
        StringProperty title = new SimpleStringProperty(initialTitle);
        columnTitles.put(column, title);

        Label label = new Label();
        label.textProperty().bind(title);
        label.getStyleClass().add("editable-column-header-label");

        TextField editor = new TextField(initialTitle);
        editor.getStyleClass().add("editable-column-header");
        editor.textProperty().bindBidirectional(title);
        editor.setPrefWidth(Math.max(45, column.getPrefWidth() - 32));
        column.widthProperty().addListener((observable, oldWidth, newWidth) ->
                editor.setPrefWidth(Math.max(45, newWidth.doubleValue() - 32)));
        editor.setOnAction(event -> table.requestFocus());
        editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && column.getGraphic() == editor) column.setGraphic(label);
        });

        label.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) return;
            column.setGraphic(editor);
            javafx.application.Platform.runLater(() -> {
                editor.requestFocus();
                editor.selectAll();
            });
            event.consume();
        });

        column.setText("");
        column.setGraphic(label);
        column.setSortable(false);
    }

    /** Replaces the current custom columns with the persisted definitions (used on workspace load). */
    public void setCustomFields(List<String> fieldNames) {
        table.getColumns().removeAll(customColumns.keySet());
        customColumns.keySet().forEach(column -> {
            Menu entry = sortMenuEntries.remove(column);
            if (entry != null) sortMenu.getItems().remove(entry);
        });
        customColumns.clear();
        if (fieldNames != null) fieldNames.forEach(this::addCustomColumn);
    }

    public List<String> customFieldsSnapshot() {
        return customColumns.values().stream().map(StringProperty::get).toList();
    }

    public void setQuickEditEnabled(boolean enabled) { inlineEditToggle.setSelected(enabled); }

    public boolean isQuickEditEnabled() { return inlineEditToggle.isSelected(); }

    private void promptNewCustomField() {
        requestNewFieldName().ifPresent(name -> {
            addCustomColumn(name);
            dataChanged.run();
        });
    }

    private java.util.Optional<String> requestNewFieldName() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add field");
        dialog.setHeaderText("Add a new contact field");
        dialog.setContentText("Field name:");
        themeService.applyTo(dialog);
        return dialog.showAndWait()
                .map(value -> value == null ? "" : value.trim())
                .filter(name -> {
                    if (name.isEmpty()) return false;
                    if (isFieldNameTaken(name)) {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Add field");
                        alert.setHeaderText("A field named \"" + name + "\" already exists.");
                        themeService.applyTo(alert);
                        alert.showAndWait();
                        return false;
                    }
                    return true;
                });
    }

    private boolean isFieldNameTaken(String name) {
        return columnTitles.values().stream().anyMatch(title -> title.get().equalsIgnoreCase(name))
                || customColumns.values().stream().anyMatch(title -> title.get().equalsIgnoreCase(name));
    }

    private StringProperty addCustomColumn(String name) {
        TableColumn<Contact, String> column = new TableColumn<>();
        column.setMinWidth(110);
        column.setPrefWidth(150);
        StringProperty title = new SimpleStringProperty(name);
        customColumns.put(column, title);
        column.setCellValueFactory(data -> data.getValue().customFieldProperty(title.get()));
        column.setComparator(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        column.setCellFactory(ignored -> new InlineTextCell());
        column.setOnEditCommit(event -> commitInlineEdit(event, (contact, value) -> contact.setCustomField(title.get(), value)));
        configureCustomHeader(column, title);
        table.getColumns().add(column);
        addSortMenuEntry(column, title);
        return title;
    }

    /** Header for user-defined columns: rename commits on Enter/focus loss, right click removes the field. */
    private void configureCustomHeader(TableColumn<Contact, String> column, StringProperty title) {
        Label label = new Label();
        label.textProperty().bind(title);
        label.getStyleClass().add("editable-column-header-label");

        TextField editor = new TextField();
        editor.getStyleClass().add("editable-column-header");
        editor.setPrefWidth(Math.max(45, column.getPrefWidth() - 32));
        column.widthProperty().addListener((observable, oldWidth, newWidth) ->
                editor.setPrefWidth(Math.max(45, newWidth.doubleValue() - 32)));

        Runnable commitRename = () -> {
            String requested = editor.getText() == null ? "" : editor.getText().trim();
            if (!requested.isEmpty() && !requested.equals(title.get()) && !isFieldNameTaken(requested)) {
                String previous = title.get();
                title.set(requested);
                contacts.forEach(contact -> contact.renameCustomField(previous, requested));
                table.refresh();
                dataChanged.run();
            }
            if (column.getGraphic() == editor) column.setGraphic(label);
        };
        editor.setOnAction(event -> table.requestFocus());
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                editor.setText(title.get());
                table.requestFocus();
                event.consume();
            }
        });
        editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) commitRename.run();
        });

        label.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) return;
            editor.setText(title.get());
            column.setGraphic(editor);
            javafx.application.Platform.runLater(() -> {
                editor.requestFocus();
                editor.selectAll();
            });
            event.consume();
        });

        MenuItem removeField = new MenuItem();
        removeField.textProperty().bind(Bindings.concat("Remove field \"", title, "\"…"));
        removeField.setOnAction(event -> removeCustomColumn(column, title));
        label.setContextMenu(new ContextMenu(removeField));

        column.setText("");
        column.setGraphic(label);
        column.setSortable(false);
    }

    private void removeCustomColumn(TableColumn<Contact, String> column, StringProperty title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remove field");
        themeService.applyTo(alert);
        alert.setHeaderText("Remove the field \"" + title.get() + "\"?");
        alert.setContentText("The values stored in this field will be deleted for every contact.");
        if (alert.showAndWait().filter(result -> result == ButtonType.OK).isEmpty()) return;
        table.getColumns().remove(column);
        customColumns.remove(column);
        Menu entry = sortMenuEntries.remove(column);
        if (entry != null) sortMenu.getItems().remove(entry);
        contacts.forEach(contact -> contact.removeCustomField(title.get()));
        dataChanged.run();
    }

    private void setupSortMenu() {
        MenuItem clearSort = new MenuItem("No sorting");
        clearSort.setOnAction(event -> clearSort());
        sortMenu.getItems().addAll(clearSort, new SeparatorMenuItem());

        columnTitles.forEach(this::addSortMenuEntry);
    }

    private void addSortMenuEntry(TableColumn<Contact, String> column, StringProperty title) {
        Menu columnMenu = new Menu();
        columnMenu.textProperty().bind(title);
        MenuItem ascending = new MenuItem();
        ascending.textProperty().bind(Bindings.concat(title, " — ascending"));
        ascending.setOnAction(event -> applySort(column, TableColumn.SortType.ASCENDING, title.get()));
        MenuItem descending = new MenuItem();
        descending.textProperty().bind(Bindings.concat(title, " — descending"));
        descending.setOnAction(event -> applySort(column, TableColumn.SortType.DESCENDING, title.get()));
        columnMenu.getItems().addAll(ascending, descending);
        sortMenu.getItems().add(columnMenu);
        sortMenuEntries.put(column, columnMenu);
    }

    private void applySort(TableColumn<Contact, String> column, TableColumn.SortType type, String title) {
        Comparator<Contact> comparator = (left, right) -> column.getComparator().compare(
                column.getCellObservableValue(left).getValue(),
                column.getCellObservableValue(right).getValue());
        sortedContacts.setComparator(type == TableColumn.SortType.ASCENDING
                ? comparator : comparator.reversed());
        pagination.setCurrentPageIndex(0);
        updateTablePage();
        sortMenu.setText(title + (type == TableColumn.SortType.ASCENDING ? " ↑" : " ↓"));
    }

    private void clearSort() {
        sortedContacts.setComparator(null);
        pagination.setCurrentPageIndex(0);
        updateTablePage();
        sortMenu.setText("Sort");
    }

    private void handleTableKeyPressed(KeyEvent event) {
        if (isShortcut(event, KeyCode.C)) {
            copySelectedContacts();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            deleteSelectedContacts();
        }
    }

    private boolean isShortcut(KeyEvent event, KeyCode key) {
        return event.getCode() == key && (event.isControlDown() || event.isMetaDown());
    }

    private List<Contact> selectedContacts() {
        Set<Contact> selected = new HashSet<>(checkedContacts);
        selected.addAll(table.getSelectionModel().getSelectedItems());
        return new ArrayList<>(selected);
    }

    private void copySelectedContacts() {
        List<Contact> selected = selectedContacts();
        if (selected.isEmpty()) return;
        StringBuilder text = new StringBuilder("Name\tCompany\tTitle\tEmail\tPhone\tTags\tDescription\n");
        for (Contact contact : selected) {
            text.append(clipboardValue(contact.nameProperty().get())).append('\t')
                    .append(clipboardValue(contact.companyProperty().get())).append('\t')
                    .append(clipboardValue(contact.titleProperty().get())).append('\t')
                    .append(clipboardValue(contact.emailProperty().get())).append('\t')
                    .append(clipboardValue(contact.phoneProperty().get())).append('\t')
                    .append(clipboardValue(contact.tagsProperty().get())).append('\t')
                    .append(clipboardValue(contact.descriptionProperty().get())).append('\n');
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    static String clipboardValue(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private void showContactDialog(Contact contact) {
        Dialog<Contact> dialog = new Dialog<>();
        dialog.setTitle(contact == null ? "Add New Contact" : "Edit Contact");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(12));
        grid.setPrefWidth(480);
        javafx.scene.layout.ColumnConstraints labels = new javafx.scene.layout.ColumnConstraints(); labels.setMinWidth(85);
        javafx.scene.layout.ColumnConstraints fields = new javafx.scene.layout.ColumnConstraints(); fields.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labels, fields);
        TextField name = new TextField();
        name.setId("contactNameField"); name.setPromptText("Full name (required)");
        TextField company = new TextField();
        TextField title = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        TextArea description = new TextArea();
        description.setPrefRowCount(3);
        ComboBox<String> tags = new ComboBox<>(FXCollections.observableArrayList(TAG_EMPTY, "Client", "Tech", "Follow-up"));
        tags.setValue(TAG_EMPTY);
        Map<StringProperty, TextField> customEditors = new LinkedHashMap<>();
        customColumns.values().forEach(fieldTitle -> customEditors.put(fieldTitle, new TextField()));
        if (contact != null) {
            name.setText(contact.nameProperty().get());
            company.setText(contact.companyProperty().get());
            title.setText(contact.titleProperty().get());
            email.setText(contact.emailProperty().get());
            phone.setText(contact.phoneProperty().get());
            String currentTag = contact.tagsProperty().get();
            tags.setValue(currentTag == null || currentTag.isBlank() ? TAG_EMPTY : currentTag);
            description.setText(contact.descriptionProperty().get());
            customEditors.forEach((fieldTitle, editor) -> editor.setText(contact.customFieldValue(fieldTitle.get())));
        }
        grid.add(new Label("Name:"), 0, 0); grid.add(name, 1, 0);
        grid.add(new Label("Company:"), 0, 1); grid.add(company, 1, 1);
        grid.add(new Label("Title:"), 0, 2); grid.add(title, 1, 2);
        grid.add(new Label("Email:"), 0, 3); grid.add(email, 1, 3);
        grid.add(new Label("Phone:"), 0, 4); grid.add(phone, 1, 4);
        grid.add(new Label("Tags:"), 0, 5); grid.add(tags, 1, 5);
        int rowIndex = 6;
        for (Map.Entry<StringProperty, TextField> custom : customEditors.entrySet()) {
            grid.add(new Label(custom.getKey().get() + ":"), 0, rowIndex);
            grid.add(custom.getValue(), 1, rowIndex);
            rowIndex++;
        }
        Label descriptionLabel = new Label("Description:");
        javafx.scene.Node descriptionEditor = DialogService.withResizeGrip(description);
        grid.add(descriptionLabel, 0, rowIndex);
        grid.add(descriptionEditor, 1, rowIndex);
        Button addFieldInDialog = new Button("＋ Add field");
        grid.add(addFieldInDialog, 1, rowIndex + 1);
        int[] descriptionRow = { rowIndex };
        addFieldInDialog.setOnAction(event -> requestNewFieldName().ifPresent(fieldName -> {
            StringProperty fieldTitle = addCustomColumn(fieldName);
            dataChanged.run();
            TextField editor = new TextField();
            customEditors.put(fieldTitle, editor);
            grid.add(new Label(fieldName + ":"), 0, descriptionRow[0]);
            grid.add(editor, 1, descriptionRow[0]);
            descriptionRow[0]++;
            GridPane.setRowIndex(descriptionLabel, descriptionRow[0]);
            GridPane.setRowIndex(descriptionEditor, descriptionRow[0]);
            GridPane.setRowIndex(addFieldInDialog, descriptionRow[0] + 1);
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
            editor.requestFocus();
        }));
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("form-error"); error.setId("contactValidationError");
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(430);
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, scroll, error); content.getStyleClass().add("event-editor");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (name.getText().isBlank()) { error.setText("Enter a name for this contact."); event.consume(); name.requestFocus(); }
            else if (!email.getText().isBlank() && !email.getText().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                error.setText("Enter a valid email address, or leave email empty."); event.consume(); email.requestFocus();
            }
        });
        dialog.setResultConverter(result -> {
            if (result != save) return null;
            String tagValue = tagStorageValue(tags.getValue());
            Contact target = contact != null ? contact
                    : new Contact(name.getText(), company.getText(), title.getText(),
                            email.getText(), phone.getText(), "Just now", tagValue, description.getText());
            if (contact != null) {
                contact.setName(name.getText());
                contact.setCompany(company.getText());
                contact.setTitle(title.getText());
                contact.setEmail(email.getText());
                contact.setPhone(phone.getText());
                contact.setTags(tagValue);
                contact.setDescription(description.getText());
            }
            customEditors.forEach((fieldTitle, editor) -> target.setCustomField(fieldTitle.get(), editor.getText()));
            return target;
        });
        dialog.showAndWait().ifPresent(saved -> {
            if (contact == null) contacts.add(0, saved);
            updatePagination();
            table.refresh();
            dataChanged.run();
        });
    }

    private void filterContacts(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            filteredContacts.setPredicate(contact -> true);
            return;
        }
        String query = searchText.toLowerCase();
        filteredContacts.setPredicate(contact ->
                safe(contact.nameProperty().get()).contains(query)
                        || safe(contact.companyProperty().get()).contains(query)
                        || safe(contact.emailProperty().get()).contains(query)
                        || safe(contact.descriptionProperty().get()).contains(query));
    }

    private String safe(String value) { return value == null ? "" : value.toLowerCase(); }

    private void updatePagination() {
        String selected = rowsPerPageCombo.getValue();
        if (selected == null) return;
        int total = sortedContacts.size();
        if (selected.equals("All")) {
            pagination.setPageCount(1);
            pagination.setCurrentPageIndex(0);
            pagination.setVisible(false);
            pagination.setManaged(false);
        } else {
            int rowsPerPage = Integer.parseInt(selected);
            pagination.setPageCount(Math.max(1, (total + rowsPerPage - 1) / rowsPerPage));
            pagination.setVisible(true);
            pagination.setManaged(true);
        }
        updateTablePage();
    }

    private void updateTablePage() {
        String selected = rowsPerPageCombo.getValue();
        if (selected == null) return;
        int total = sortedContacts.size();
        if (selected.equals("All")) {
            table.setItems(FXCollections.observableArrayList(sortedContacts));
            paginationInfoLabel.setText(String.format("Showing all %d Contacts", total));
            return;
        }
        int rowsPerPage = Integer.parseInt(selected);
        int from = pagination.getCurrentPageIndex() * rowsPerPage;
        int to = Math.min(from + rowsPerPage, total);
        if (from >= total) {
            table.setItems(FXCollections.observableArrayList());
            paginationInfoLabel.setText("Showing 0-0 of " + total + " Contacts");
        } else {
            table.setItems(FXCollections.observableArrayList(sortedContacts.subList(from, to)));
            paginationInfoLabel.setText(String.format("Showing %d-%d of %d Contacts", from + 1, to, total));
        }
    }
}
