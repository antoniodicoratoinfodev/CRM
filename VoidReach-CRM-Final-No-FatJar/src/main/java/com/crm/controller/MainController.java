package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.CrmTrash;
import com.crm.service.WorkspaceHistoryService;
import com.crm.model.UserAccount;
import com.crm.repository.ExportOwner;
import com.crm.repository.ImportedWorkspace;
import com.crm.repository.LocalUserRepository;
import com.crm.repository.UserRepository;
import com.crm.service.CrmWorkspaceService;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import com.crm.service.TaskScheduleService;
import com.crm.service.WorkspaceSearchService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxmisc.richtext.CodeArea;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * FXML composition root for the main window.
 *
 * <p>Feature behavior is delegated to focused controllers; this class only wires
 * injected controls, coordinates the shared workspace snapshot, and exposes the
 * event methods referenced by MainView.fxml.</p>
 */
public final class MainController {
    private static final double LEFT_SIDEBAR_DEFAULT_WIDTH = 216;
    private static final double LEFT_SIDEBAR_MIN_WIDTH = 176;
    private static final double LEFT_SIDEBAR_MAX_WIDTH = 360;
    private static final double RIGHT_SIDEBAR_DEFAULT_WIDTH = 296;
    private static final double RIGHT_SIDEBAR_MIN_WIDTH = 260;
    private static final double RIGHT_SIDEBAR_MAX_WIDTH = 440;
    private static final double SIDEBAR_COLLAPSE_THRESHOLD = 52;
    private static final double MIN_WORKSPACE_WIDTH = 720;
    private static final double RESIZE_HANDLE_WIDTH = 6;

    @FXML private BorderPane appShell;
    @FXML private HBox leftSidebarWrapper;
    @FXML private HBox rightSidebarWrapper;
    @FXML private VBox leftPanel;
    @FXML private TableView<Contact> contactsTable;
    @FXML private TableColumn<Contact, String> nameColumn;
    @FXML private TableColumn<Contact, String> companyColumn;
    @FXML private TableColumn<Contact, String> titleColumn;
    @FXML private TableColumn<Contact, String> emailColumn;
    @FXML private TableColumn<Contact, String> phoneColumn;
    @FXML private TableColumn<Contact, String> lastInteractionColumn;
    @FXML private TableColumn<Contact, String> tagsColumn;
    @FXML private TableColumn<Contact, String> descriptionColumn;
    @FXML private TableColumn<Contact, Boolean> selectColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> rowsPerPageCombo;
    @FXML private Pagination contactsPagination;
    @FXML private Label paginationInfoLabel;
    @FXML private Button selectContactsBtn;
    @FXML private MenuButton contactsSortMenu;
    @FXML private ToggleButton contactsInlineEditToggle;
    @FXML private Button addFieldBtn;

    @FXML private ScrollPane homeView;
    @FXML private ScrollPane dashboardView;
    @FXML private VBox contactsView;
    @FXML private VBox calendarView;
    @FXML private VBox tasksView;
    @FXML private VBox notesView;
    @FXML private ScrollPane settingsView;
    @FXML private VBox genericView;
    @FXML private Label genericTitle;
    @FXML private FontIcon genericIcon;
    @FXML private VBox sidebarContainer;
    @FXML private VBox rightPanel;
    @FXML private Region leftResizeHandle;
    @FXML private Region rightResizeHandle;
    @FXML private Button leftSidebarToggleButton;
    @FXML private Button agendaToggleButton;
    @FXML private Button themeToggleBtn;
    @FXML private FontIcon themeToggleIcon;
    @FXML private Label currentUserLabel;
    @FXML private Label saveStatusLabel;
    @FXML private VBox mainSurface;
    @FXML private VBox topbarStatusGroup;
    @FXML private VBox accountDetails;
    @FXML private Label topbarTitleLabel;
    @FXML private Label searchShortcutLabel;
    @FXML private Button workspaceSearchButton;
    @FXML private Button helpButton;
    @FXML private Button remindersButton;
    @FXML private Label reminderCountLabel;
    @FXML private HBox homeFocusSummary;
    @FXML private FontIcon homeFocusIcon;
    @FXML private Label homeFocusTitleLabel;
    @FXML private Label homeFocusDetailLabel;
    @FXML private Button homeFocusButton;
    @FXML private Button accountMenuButton;
    @FXML private FontIcon defaultAvatarIcon;
    @FXML private ImageView avatarImage;
    @FXML private Label settingsAccountNameLabel;
    @FXML private Label settingsAccountEmailLabel;
    @FXML private FontIcon settingsDefaultAvatarIcon;
    @FXML private ImageView settingsAvatarImage;
    @FXML private Button settingsDarkThemeBtn;
    @FXML private Button settingsLightThemeBtn;
    @FXML private Button settingsBlueGrayThemeBtn;
    @FXML private Button settingsGrayBlueThemeBtn;
    @FXML private Label settingsDarkThemeStatus;
    @FXML private Label settingsLightThemeStatus;
    @FXML private Label settingsBlueGrayThemeStatus;
    @FXML private Label settingsGrayBlueThemeStatus;

    @FXML private AnchorPane timeLabelsContainer;
    @FXML private AnchorPane calendarTimelineArea;
    @FXML private HBox calendarContentRow;
    @FXML private ScrollPane calendarScrollPane;
    @FXML private DatePicker calendarDatePicker;
    @FXML private ComboBox<String> viewModeCombo;
    @FXML private Label selectedPeriodLabel;
    @FXML private Label calendarZoomLabel;
    @FXML private Label miniMonthYearLabel;
    @FXML private GridPane miniCalendarGrid;
    @FXML private Label activitiesTitle;
    @FXML private VBox upcomingActivitiesList;

    @FXML private TextField taskSearchField;
    @FXML private ComboBox<String> taskFilterCombo;
    @FXML private VBox taskListContainer;
    @FXML private Label tasksEmptyLabel;
    @FXML private Label tasksTotalCountLabel;
    @FXML private Label tasksTodayCountLabel;
    @FXML private Label tasksUpcomingCountLabel;
    @FXML private Label tasksCompletedCountLabel;

    @FXML private VBox notesLibraryPane;
    @FXML private VBox noteEditorPane;
    @FXML private TextField notesSearchField;
    @FXML private TilePane notesGrid;
    @FXML private Label notesEmptyLabel;
    @FXML private Label notesCountLabel;
    @FXML private Button notesBackButton;
    @FXML private ScrollPane notesBreadcrumbScroll;
    @FXML private HBox notesBreadcrumbItems;
    @FXML private TextField noteTitleField;
    @FXML private Label noteFormatLabel;
    @FXML private ComboBox<NotesController.FolderOption> noteFolderCombo;
    @FXML private ComboBox<NotesController.TaskOption> noteTaskCombo;
    @FXML private MenuButton noteOpenTaskButton;
    @FXML private HBox markdownToolbar;
    @FXML private ComboBox<String> noteFontFamilyCombo;
    @FXML private ComboBox<Double> noteFontSizeCombo;
    @FXML private ComboBox<String> noteFontWeightCombo;
    @FXML private ToggleButton noteBoldToggle;
    @FXML private ToggleButton noteItalicToggle;
    @FXML private HBox notePreviewSettingsBar;
    @FXML private ComboBox<String> notePreviewFontFamilyCombo;
    @FXML private ComboBox<Double> notePreviewFontSizeCombo;
    @FXML private ColorPicker notePreviewColorPicker;
    @FXML private ToggleButton notePreviewToggle;
    @FXML private CodeArea noteContentArea;
    @FXML private StackPane notePreviewPane;
    @FXML private Label noteEditorStatus;

    @FXML private Label homeGreetingLabel;
    @FXML private Label homeDateLabel;
    @FXML private Label homeContactsCountLabel;
    @FXML private Label homeTodayCountLabel;
    @FXML private Label homeWeekCountLabel;
    @FXML private Label homeNextTitleLabel;
    @FXML private Label homeNextTimeLabel;
    @FXML private VBox homeTodayList;
    @FXML private VBox homeUpcomingList;
    @FXML private VBox homeContactsList;
    @FXML private Label dashboardContactsCountLabel;
    @FXML private Label dashboardActivitiesCountLabel;
    @FXML private Label dashboardWeekCountLabel;
    @FXML private Label dashboardHoursCountLabel;
    @FXML private PieChart dashboardTagsChart;
    @FXML private BarChart<String, Number> dashboardActivityChart;
    @FXML private VBox dashboardInteractionsList;

    private final CrmWorkspaceService workspaceService = new CrmWorkspaceService();
    private final UserRepository userRepository = new LocalUserRepository();
    private ThemeService themeService;
    private DialogService dialogService;
    private ContactsController contactsController;
    private CalendarController calendarController;
    private TasksController tasksController;
    private NotesController notesController;
    private AccountController accountController;
    private OverviewController overviewController;
    private NavigationController navigationController;
    private WorkspaceSearchController workspaceSearchController;
    private Timeline clockRefresh;
    private final javafx.animation.PauseTransition noteChangeDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));
    private boolean closingWorkspace;
    private final WorkspaceHistoryService history = new WorkspaceHistoryService();
    private CrmTrash trash = CrmTrash.EMPTY;
    private final Map<String, String> workspacePreferences = new HashMap<>();
    private boolean restoringHistory;
    private boolean contextualAgenda;
    private boolean previousAgenda;
    private final java.util.Set<String> visibleReminders = new java.util.HashSet<>();
    private boolean readableWorkspace = true;
    private Window observedWindow;
    private final ChangeListener<Boolean> windowVisibilityListener = (observable, oldValue, showing) -> {
        if (showing) clockRefresh.play();
        else clockRefresh.stop();
    };
    private Runnable logoutAction;
    private UserAccount currentUser;
    private boolean loadingWorkspace = true;
    private double leftExpandedWidth = LEFT_SIDEBAR_DEFAULT_WIDTH;
    private double rightExpandedWidth = RIGHT_SIDEBAR_DEFAULT_WIDTH;
    private boolean leftAutomaticallyCollapsed;
    private boolean rightAutomaticallyCollapsed;
    private boolean adjustingResponsiveSidebars;

    @FXML
    public void initialize() {
        themeService = new ThemeService(this::ownerWindow);
        dialogService = new DialogService(themeService);
        initializeResizableSidebars();
        navigationController = new NavigationController(homeView, dashboardView,
                contactsView, calendarView, tasksView, notesView, settingsView, genericView,
                genericTitle, genericIcon, sidebarContainer);
        overviewController = new OverviewController(
                homeGreetingLabel, homeDateLabel,
                homeContactsCountLabel, homeTodayCountLabel, homeWeekCountLabel,
                homeNextTitleLabel, homeNextTimeLabel,
                homeTodayList, homeUpcomingList, homeContactsList,
                dashboardContactsCountLabel, dashboardActivitiesCountLabel,
                dashboardWeekCountLabel, dashboardHoursCountLabel,
                dashboardTagsChart, dashboardActivityChart, dashboardInteractionsList);
        contactsController = new ContactsController(contactsTable, nameColumn, companyColumn,
                titleColumn, emailColumn, phoneColumn, lastInteractionColumn, tagsColumn,
                descriptionColumn, selectColumn, searchField, rowsPerPageCombo, contactsPagination,
                paginationInfoLabel, selectContactsBtn, contactsSortMenu, contactsInlineEditToggle,
                addFieldBtn, themeService, this::handleDataChanged);
        calendarController = new CalendarController(calendarView, timeLabelsContainer,
                calendarTimelineArea, calendarContentRow, calendarScrollPane, calendarDatePicker,
                viewModeCombo, selectedPeriodLabel, calendarZoomLabel,
                miniMonthYearLabel, miniCalendarGrid, activitiesTitle, upcomingActivitiesList, themeService,
                dialogService, this::handleDataChanged, navigationController::showCalendar);
        notesController = new NotesController(notesLibraryPane, noteEditorPane, notesSearchField,
                notesGrid, notesEmptyLabel, notesCountLabel, noteTitleField, noteFormatLabel,
                notesBackButton, notesBreadcrumbScroll, notesBreadcrumbItems, noteFolderCombo,
                noteTaskCombo, noteOpenTaskButton, markdownToolbar, notePreviewToggle,
                noteFontFamilyCombo, noteFontSizeCombo, noteFontWeightCombo, noteBoldToggle, noteItalicToggle,
                notePreviewSettingsBar, notePreviewFontFamilyCombo, notePreviewFontSizeCombo, notePreviewColorPicker,
                noteContentArea, notePreviewPane, noteEditorStatus,
                themeService, new NotesController.NoteActions() {
                    @Override public void dataChanged() {
                        setSaveStatus("Unsaved changes");
                        noteChangeDebounce.playFromStart();
                    }
                    @Override public void notePresentationChanged() { calendarController.refreshNoteLinks(); }
                    @Override public void showNotes() { navigationController.showNotes(); }
                    @Override public void openTask(LocalDate date, com.crm.model.Task task) {
                        calendarController.editTask(date, task);
                    }
                });
        calendarController.setNoteIntegration(new CalendarController.NoteIntegration() {
            @Override public List<com.crm.model.Note> notes() { return notesController.snapshot(); }
            @Override public List<com.crm.model.NoteFolder> folders() { return notesController.foldersSnapshot(); }
            @Override public List<com.crm.model.Note> notesForTask(String taskId) {
                return notesController.notesForTask(taskId);
            }
            @Override public void openNote(String noteId) { notesController.openById(noteId); }
        });
        tasksController = new TasksController(taskSearchField, taskFilterCombo, taskListContainer,
                tasksEmptyLabel, tasksTotalCountLabel, tasksTodayCountLabel,
                tasksUpcomingCountLabel, tasksCompletedCountLabel, themeService,
                new TasksController.TaskActions() {
                    @Override public void edit(LocalDate date, com.crm.model.Task task) {
                        calendarController.editTask(date, task);
                    }
                    @Override public void delete(LocalDate date, com.crm.model.Task task) {
                        calendarController.deleteTask(date, task);
                    }
                    @Override public void setCompleted(LocalDate date, com.crm.model.Task task, boolean completed) {
                        calendarController.setTaskCompleted(date, task, completed);
                    }
                    @Override public void openCalendar(LocalDate date) {
                        calendarController.showTaskInCalendar(date);
                    }
                    @Override public List<com.crm.model.Note> linkedNotes(String taskId) {
                        return notesController.notesForTask(taskId);
                    }
                    @Override public void openNote(String noteId) {
                        notesController.openById(noteId);
                    }
                });
        accountController = new AccountController(currentUserLabel, accountMenuButton,
                defaultAvatarIcon, avatarImage, themeService, dialogService);
        accountController.setDataTransferActions(this::exportData, this::importData);
        bindSettingsAccountPresentation();

        navigationController.initialize();
        navigationController.setOnNavigate(title -> {
            topbarTitleLabel.setText(title);
            boolean focusedSection = title.equals("Notes") || title.equals("Calendar");
            if (focusedSection && !contextualAgenda) { previousAgenda = rightSidebarWrapper.isManaged(); contextualAgenda = true; setSidebarExpanded(false, false); }
            else if (!focusedSection && contextualAgenda) { contextualAgenda = false; if (previousAgenda) setSidebarExpanded(false, true); }
        });
        contactsController.initialize();
        calendarController.initialize();
        calendarController.setContacts(contactsController::snapshot);
        calendarController.setArchiveDeleted((date, task) -> {
            Map<LocalDate, List<com.crm.model.Task>> archived = new HashMap<>();
            trash.tasks().forEach((day, entries) -> archived.put(day, new ArrayList<>(entries)));
            archived.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task);
            trash = new CrmTrash(trash.contacts(), archived, trash.notes());
        });
        overviewController.attachInsights((VBox) ((ScrollPane) dashboardView).getContent(), calendarController::showTaskInCalendar,
                () -> com.crm.model.CalendarPreferences.from(calendarController.preferencesSnapshot()));
        tasksController.initialize();
        notesController.initialize();
        initializeFocusedNotes();
        ContactDetailController contactDetails = new ContactDetailController(themeService, new ContactDetailController.Actions() {
            @Override public Map<LocalDate, List<com.crm.model.Task>> tasks() { return calendarController.tasksSnapshot(); }
            @Override public List<com.crm.model.Note> notes() { return notesController.snapshot(); }
            @Override public void edit(Contact contact) { contactsController.editRecord(contact); }
            @Override public void changed() { contactsTable.refresh(); handleDataChanged(); }
            @Override public void followUp(Contact contact) { calendarController.createFollowUp(contact, LocalDate.now().plusDays(3)); }
            @Override public void newNote(Contact contact) { notesController.createForContact(contact); }
            @Override public void openTask(LocalDate date, com.crm.model.Task task) { calendarController.editTask(date, task); }
            @Override public void openNote(String id) { notesController.openById(id); }
        });
        contactsController.setOpenDetails(contactDetails::show);
        overviewController.setActions(calendarController::editTask, contact -> {
            navigationController.showContacts();
            contactsController.openById(contact.getId());
        });
        initializeWorkspaceActions();
        noteChangeDebounce.setOnFinished(event -> handleDataChanged());
        overviewController.refresh(List.of(), Map.of());
        updateThemeButton();
        themeToggleBtn.sceneProperty().addListener((observable, oldScene, newScene) -> {
            themeService.applyTo(newScene);
            if (newScene != null) installWorkspaceShortcuts(newScene);
        });
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction) {
        setCurrentUser(user, logoutAction, null, 0);
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction,
                               BufferedImage preloadedAvatar, int preloadedPixelSize) {
        this.currentUser = user;
        this.logoutAction = logoutAction;
        settingsAccountEmailLabel.setText(user.getEmail());
        themeService.restore(user.getPreferredTheme());
        themeService.applyTo(themeToggleBtn.getScene());
        updateThemeButton();
        calendarController.refreshTheme();
        notesController.refreshTheme();
        overviewController.setUser(user);
        refreshOverview();
        accountController.setCurrentUser(
                user, this::logout, preloadedAvatar, preloadedPixelSize);
        loadingWorkspace = true;
        setSaveStatus("Loading data…");
        workspaceService.openAsync(user).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            try {
                if (failure == null) applyUserData(snapshot);
                else {
                    LocalDate today = LocalDate.now();
                    applyUserData(new CrmDataSnapshot(new ArrayList<>(), new HashMap<>(), today, "Day", 1.0));
                    dialogService.showError("Local data cannot be read",
                            "Neither the data file nor its backup could be recovered. "
                                    + "Saving is disabled to protect the original file. Restore a backup or import a valid workspace from Settings.");
                }
            } finally {
                loadingWorkspace = false;
                readableWorkspace = failure == null;
                setSaveStatus(failure == null ? "Data loaded" : "Data not loaded");
            }
        }));
    }

    public void requestInitialFocus() {
        Platform.runLater(homeView::requestFocus);
    }

    private void applyUserData(CrmDataSnapshot snapshot) {
        snapshot = snapshot.detached();
        trash = snapshot.trash();
        workspacePreferences.clear(); workspacePreferences.putAll(snapshot.preferences());
        calendarController.applyPreferences(snapshot.preferences());
        contactsController.setCustomFields(snapshot.contactCustomFields());
        contactsController.setQuickEditEnabled(snapshot.contactsQuickEdit());
        contactsController.setContacts(snapshot.contacts());
        calendarController.applyState(snapshot.tasksByDate(), snapshot.selectedDate(),
                snapshot.calendarViewMode(), snapshot.calendarZoom());
        notesController.applyState(snapshot.notes(), snapshot.noteFolders(), calendarController.tasksSnapshot());
        refreshOverview();
        if (!restoringHistory) history.reset(snapshot);
    }

    private void handleDataChanged() {
        refreshOverview();
        saveCurrentData();
    }

    private void refreshOverview() {
        Map<LocalDate, List<com.crm.model.Task>> tasks = calendarController.tasksSnapshot();
        overviewController.refresh(contactsController.snapshot(), tasks);
        tasksController.refresh(tasks);
        notesController.refreshTasks(tasks);
        refreshAttention(tasks);
    }

    private void saveCurrentData() {
        if (loadingWorkspace || calendarController.selectedDate() == null) return;
        if (!readableWorkspace) { setSaveStatus("Read-only — restore or import a valid backup"); return; }
        CrmDataSnapshot snapshot = CrmDataSnapshot.detachedCopyOf(contactsController.snapshot(),
                calendarController.tasksSnapshot(), notesController.snapshot(), notesController.foldersSnapshot(),
                calendarController.selectedDate(), calendarController.viewMode(), calendarController.zoom(),
                contactsController.customFieldsSnapshot(), contactsController.isQuickEditEnabled());
        workspacePreferences.putAll(calendarController.preferencesSnapshot());
        snapshot = snapshot.withExtras(workspacePreferences, trash.detached(contactsController.customFieldsSnapshot()));
        if (!restoringHistory) snapshot = history.record(snapshot);
        trash = snapshot.trash();
        workspaceService.requestSave(snapshot, state -> Platform.runLater(() -> handleSaveState(state)));
    }

    private Window ownerWindow() {
        Scene scene = themeToggleBtn == null ? null : themeToggleBtn.getScene();
        return scene == null ? null : scene.getWindow();
    }

    /** Writes the current account's workspace to a user-chosen desktop-compatible file. */
    private void exportData() {
        if (currentUser == null) return;
        if (currentUser.isVaultEnabled() && !dialogService.confirmWarning("Export without encryption",
                "Portable workspace files are not encrypted. They include contacts, tasks, notes and deleted records. Store the export privately.", "Continue")) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export data");
        chooser.setInitialFileName("VoidReach-CRM-" + LocalDate.now() + ".properties");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("VoidReach CRM data", "*.properties"));
        File selected = chooser.showSaveDialog(ownerWindow());
        if (selected == null) return;
        Path target = selected.toPath();
        setSaveStatus("Exporting…");
        saveCurrentData();
        workspaceService.exportAsync(target).whenComplete((ignored, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) {
                        setSaveStatus("Export failed");
                        dialogService.showError("Export failed",
                                "The data could not be exported to the selected file.");
                    } else {
                        setSaveStatus("Data exported");
                        dialogService.showInfo("Export complete", "Data exported in desktop-compatible format.");
                    }
                }));
    }

    /** Loads a portable file, warning first when it belongs to a different account, then applies it. */
    private void importData() {
        if (currentUser == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import data");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("VoidReach CRM data", "*.properties"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File selected = chooser.showOpenDialog(ownerWindow());
        if (selected == null) return;
        Path source = selected.toPath();
        setSaveStatus("Importing…");
        CompletableFuture.supplyAsync(() -> workspaceService.readImport(source)).whenComplete((imported, failure) ->
                Platform.runLater(() -> {
                    if (failure != null) {
                        setSaveStatus("Import failed");
                        dialogService.showError("Import failed",
                                "The selected file could not be read as VoidReach CRM data.");
                        return;
                    }
                    ExportOwner owner = imported.owner();
                    if (!imported.warnings().isEmpty() && !dialogService.confirmWarning("Some imported records are damaged",
                            imported.warnings().size() + " records or settings cannot be read. Import only the readable data? The source file is left unchanged.\n\n"
                                    + String.join("\n", imported.warnings().stream().limit(8).toList()), "Use readable data")) {
                        setSaveStatus("Import cancelled"); return;
                    }
                    if (owner != null && currentUser != null && !owner.email().equalsIgnoreCase(currentUser.getEmail())) {
                        String who = owner.name() == null || owner.name().isBlank()
                                ? owner.email() : owner.name() + " (" + owner.email() + ")";
                        boolean proceed = dialogService.confirmWarning("Data from another account",
                                "This file was exported by " + who + ", but you are signed in as "
                                        + currentUser.getEmail() + ".\n\nImporting it will replace your current "
                                        + "workspace with that account's data.",
                                "Import anyway");
                        if (!proceed) {
                            setSaveStatus("Import cancelled");
                            return;
                        }
                    }
                    applyImportedData(imported.snapshot());
                }));
    }

    private void applyImportedData(CrmDataSnapshot snapshot) {
        if (!dialogService.confirmWarning("Replace workspace",
                "Import replaces this workspace. A checkpoint of your current readable data will be kept.", "Import")) return;
        replaceWorkspace(snapshot, "Import complete");
    }

    private void replaceWorkspace(CrmDataSnapshot snapshot, String successTitle) {
        noteChangeDebounce.stop();
        saveCurrentData();
        loadingWorkspace = true;
        if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(true);
        CompletableFuture<?> checkpoint = readableWorkspace ? workspaceService.checkpointAsync() : CompletableFuture.completedFuture(null);
        checkpoint.whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                loadingWorkspace = false;
                if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(false);
                dialogService.showError("Workspace unchanged", "The safety checkpoint failed. Nothing was replaced.");
                return;
            }
            applyUserData(snapshot);
            readableWorkspace = true; loadingWorkspace = false;
            saveCurrentData();
            workspaceService.flushAsync().whenComplete((saved, saveFailure) -> Platform.runLater(() -> {
                if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(false);
                if (saveFailure != null) dialogService.showError("Save failed", "The imported workspace is open in memory but not yet saved. Keep the app open and retry.");
                else dialogService.showInfo(successTitle, "Your workspace has been saved. Previous checkpoints are available in Backups & recovery.");
            }));
        }));
    }

    private void logout() {
        requestClose(() -> { if (logoutAction != null) logoutAction.run(); });
    }

    /** Used by both the title-bar close button and sign out. */
    public void requestClose(Runnable onSaved) {
        if (closingWorkspace || loadingWorkspace) return;
        closingWorkspace = true;
        noteChangeDebounce.stop();
        saveCurrentData();
        clockRefresh.stop();
        loadingWorkspace = true;
        if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(true);
        setSaveStatus("Final save…");
        workspaceService.closeAsync().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            loadingWorkspace = false;
            if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(false);
            if (failure != null) {
                closingWorkspace = false;
                clockRefresh.play();
                setSaveStatus("Save failed — workspace kept open");
                dialogService.showError("Workspace kept open",
                        "The final save failed. Your work is still here. Check disk access and try closing again.");
                return;
            }
            onSaved.run();
        }));
    }

    private void handleSaveState(CrmWorkspaceService.SaveState state) {
        if (state == CrmWorkspaceService.SaveState.SAVING) setSaveStatus("Saving…");
        else if (state == CrmWorkspaceService.SaveState.SAVED) setSaveStatus(
                noteChangeDebounce.getStatus() == javafx.animation.Animation.Status.RUNNING ? "Unsaved changes" : "Saved");
        else {
            setSaveStatus("Save failed");
            if (!closingWorkspace) dialogService.showError("Data not saved",
                    "The data could not be saved to disk. Your work remains open in this session so you can try again.");
        }
    }

    private void setSaveStatus(String status) {
        if (saveStatusLabel != null) saveStatusLabel.setText(status);
        if (noteEditorStatus != null) noteEditorStatus.setText(status);
    }

    @FXML private void handleAccountMenu() { accountController.showMenu(); }
    @FXML private void handleNavigation(ActionEvent event) { navigationController.navigate(event); }
    @FXML private void handleSettingsProfile() { accountController.editProfile(); }
    @FXML private void handleSettingsAvatar() { accountController.updateAvatar(); }
    @FXML private void handleSettingsSecurity() { accountController.changePassword(); }
    @FXML private void handleLocalProtection() {
        if (currentUser == null || loadingWorkspace || !readableWorkspace) return;
        new LocalProtectionController(currentUser, themeService, (operation, completion) -> {
            noteChangeDebounce.stop(); saveCurrentData(); loadingWorkspace = true;
            if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(true);
            setSaveStatus("Updating local protection…");
            workspaceService.maintenanceAsync(operation).whenComplete((ignored, failure) -> Platform.runLater(() -> {
                loadingWorkspace = false;
                if (ownerWindow() != null) ownerWindow().getScene().getRoot().setDisable(false);
                setSaveStatus(failure == null ? "Saved" : "Protection update needs attention");
                completion.accept(failure);
            }));
        }).show();
    }
    @FXML private void handleSettingsExport() { exportData(); }
    @FXML private void handleSettingsImport() { importData(); }
    @FXML private void handleSettingsBackups() {
        if (currentUser == null) return;
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Backups & recovery"); themeService.applyTo(dialog);
        ListView<Path> files = new ListView<>(); files.setPrefSize(550, 260);
        files.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty); setText(empty || path == null ? null : path.getFileName().toString());
            }
        });
        Label message = new Label("Loading backups…"); message.setWrapText(true);
        Runnable refresh = () -> workspaceService.listBackupsAsync().whenComplete((paths, failure) -> Platform.runLater(() -> {
            if (failure != null) message.setText("Backups could not be listed. Check access to the data folder.");
            else { files.getItems().setAll(paths); message.setText(paths.isEmpty() ? "No backups yet. Create a checkpoint to keep this version." :
                    "Automatic snapshots rotate. Manual checkpoints stay until you remove their files."); }
        }));
        Button create = new Button("Create checkpoint"); create.getStyleClass().add("btn-primary"); create.setDisable(!readableWorkspace);
        create.setOnAction(event -> {
            saveCurrentData(); create.setDisable(true); message.setText("Saving checkpoint…");
            workspaceService.checkpointAsync().whenComplete((path, failure) -> Platform.runLater(() -> {
                create.setDisable(false); if (failure != null) message.setText("Checkpoint failed. Your existing workspace and backups are unchanged."); else refresh.run();
            }));
        });
        Button preview = new Button("Preview / restore"); preview.getStyleClass().add("btn-secondary"); preview.disableProperty().bind(files.getSelectionModel().selectedItemProperty().isNull());
        preview.setOnAction(event -> workspaceService.readBackupAsync(files.getSelectionModel().getSelectedItem()).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            if (failure != null) { message.setText("This backup could not be read. Try another snapshot."); return; }
            int count = snapshot.tasksByDate().values().stream().mapToInt(List::size).sum();
            String detail = snapshot.contacts().size() + " contacts · " + count + " tasks/events · " + snapshot.notes().size() + " notes\n\n"
                    + "Restore this workspace? A checkpoint of the current readable workspace is created first.";
            if (dialogService.confirmWarning("Preview backup", detail, "Restore")) { dialog.close(); replaceWorkspace(snapshot, "Backup restored"); }
        })));
        VBox content = new VBox(12, new Label("Keep versions you can return to"), files, new javafx.scene.layout.FlowPane(8, 8, create, preview), message);
        content.getStyleClass().add("event-editor"); dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        refresh.run(); dialog.showAndWait();
    }
    @FXML private void handleSettingsSignOut() {
        if (dialogService.confirmWarning("Sign out?",
                "Pending changes will be saved before the session closes.", "Sign out")) {
            accountController.logout();
        }
    }
    @FXML private void handleSettingsDarkTheme() { applyTheme(ThemeService.Theme.DARK); }
    @FXML private void handleSettingsLightTheme() { applyTheme(ThemeService.Theme.LIGHT); }
    @FXML private void handleSettingsBlueGrayTheme() { applyTheme(ThemeService.Theme.BLUE_GRAY); }
    @FXML private void handleSettingsGrayBlueTheme() { applyTheme(ThemeService.Theme.GRAY_BLUE); }
    @FXML private void handleAddContact() {
        navigationController.showContacts();
        contactsController.addContact();
    }
    @FXML private void handleAddTask() { calendarController.createTask(LocalDate.now()); }
    @FXML private void handleAddNote() {
        navigationController.showNotes();
        notesController.createNote();
    }
    @FXML private void handleAddNoteFolder() { notesController.createFolder(); }
    @FXML private void handleOpenNotesRoot() { notesController.navigateUp(); }
    @FXML private void handleCloseNote() { notesController.closeEditor(); }
    @FXML private void handleDeleteNote() { notesController.deleteCurrent(); }
    @FXML private void handleOpenLinkedTask() { notesController.openLinkedTask(); }
    @FXML private void handleToggleNotePreview() { notesController.togglePreview(); }
    @FXML private void handleMarkdownHeading() { notesController.markdownHeading(); }
    @FXML private void handleMarkdownBold() { notesController.markdownBold(); }
    @FXML private void handleMarkdownItalic() { notesController.markdownItalic(); }
    @FXML private void handleMarkdownLink() { notesController.markdownLink(); }
    @FXML private void handleMarkdownCode() { notesController.markdownCode(); }
    @FXML private void handleMarkdownChecklist() { notesController.markdownChecklist(); }
    @FXML private void handleResetNoteTypography() { notesController.resetTypography(); }
    @FXML private void handleResetPreviewTypography() { notesController.resetPreviewTypography(); }
    @FXML private void handleToggleContactSelection() { contactsController.toggleSelection(); }
    @FXML private void handleDeleteContact() { contactsController.deleteSelectedContacts(); }
    @FXML private void handleMiniPrevMonth() { calendarController.previousMiniMonth(); }
    @FXML private void handleMiniNextMonth() { calendarController.nextMiniMonth(); }
    @FXML private void handleToday() { calendarController.today(); }
    @FXML private void handlePrevDay() { calendarController.previousPeriod(); }
    @FXML private void handleNextDay() { calendarController.nextPeriod(); }
    @FXML private void handleCalendarZoomIn() { calendarController.zoomIn(); }
    @FXML private void handleCalendarZoomOut() { calendarController.zoomOut(); }
    @FXML private void handleCalendarZoomReset() { calendarController.resetZoom(); }
    @FXML private void handleOpenToday() {
        calendarController.today();
        navigationController.showCalendar();
    }
    @FXML private void handleOpenContacts() { navigationController.showContacts(); }
    @FXML private void handleToggleNavigation() {
        setSidebarExpanded(true, !leftSidebarWrapper.isManaged());
    }
    @FXML private void handleToggleAgenda() {
        setSidebarExpanded(false, !rightSidebarWrapper.isManaged());
    }

    private void initializeWorkspaceActions() {
        workspaceSearchController = new WorkspaceSearchController(themeService,
                () -> new WorkspaceSearchService(contactsController.snapshot(), contactsController.customFieldsSnapshot(),
                        calendarController.tasksSnapshot(), notesController.snapshot()),
                this::openSearchResult, List.of(
                new WorkspaceSearchController.Command("New task", "Schedule your next activity", "fas-plus", this::handleAddTask),
                new WorkspaceSearchController.Command("New contact", "Add a person to your address book", "fas-user-plus", this::handleAddContact),
                new WorkspaceSearchController.Command("New note", "Capture an idea or meeting notes", "far-file-alt", this::handleAddNote),
                new WorkspaceSearchController.Command("Review due tasks", "Overdue activities and today's work", "far-bell", this::handleReviewTasks),
                new WorkspaceSearchController.Command("Go to Home", "Your daily overview", "fas-home", navigationController::showHome),
                new WorkspaceSearchController.Command("Go to Contacts", "People and companies", "fas-user-friends", navigationController::showContacts),
                new WorkspaceSearchController.Command("Go to Calendar", "Open today's schedule", "far-calendar-alt", this::handleOpenToday),
                new WorkspaceSearchController.Command("Go to Tasks", "Manage your activities", "fas-tasks", navigationController::showTasks),
                new WorkspaceSearchController.Command("Go to Notes", "Open your note library", "far-sticky-note", () -> {
                    notesController.closeEditor();
                    navigationController.showNotes();
                }),
                new WorkspaceSearchController.Command("Go to Dashboard", "Workspace insights", "fas-chart-bar", navigationController::showDashboard),
                new WorkspaceSearchController.Command("Go to Settings", "Appearance, account, and backups", "fas-cog", navigationController::showSettings)
        ));
        searchShortcutLabel.setText(shortcutName() + " K");
        mainSurface.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            setControlVisible(accountDetails, newWidth.doubleValue() >= 980);
            setControlVisible(topbarStatusGroup, newWidth.doubleValue() >= 720);
            setControlVisible(helpButton, newWidth.doubleValue() >= 720);
            setControlVisible(searchShortcutLabel, newWidth.doubleValue() >= 620);
        });
        clockRefresh = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            if (ownerWindow() == null || !ownerWindow().isShowing()) {
                clockRefresh.stop();
                return;
            }
            Map<LocalDate, List<com.crm.model.Task>> tasks = calendarController.tasksSnapshot();
            refreshAttention(tasks);
            overviewController.refresh(contactsController.snapshot(), tasks);
            if (tasksView.isVisible()) tasksController.refreshClock();
            if (calendarView.isVisible()) calendarController.refreshTheme();
            if (!workspaceService.backupFailure().isBlank()) setSaveStatus(workspaceService.backupFailure());
            showDueReminders();
        }));
        clockRefresh.setCycleCount(Timeline.INDEFINITE);
        tasksView.visibleProperty().addListener((observable, wasVisible, visible) -> {
            if (visible) tasksController.refreshClock();
        });
    }

    private void showDueReminders() {
        if (currentUser == null || loadingWorkspace || closingWorkspace) return;
        for (var reminder : com.crm.service.ReminderService.due(calendarController.tasksSnapshot(), LocalDateTime.now()).stream().limit(3).toList()) {
            if (!visibleReminders.add(reminder.key())) continue;
            Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Reminder"); themeService.applyTo(dialog);
            dialog.initModality(javafx.stage.Modality.NONE);
            dialog.setHeaderText(reminder.task().getTitle()); dialog.setContentText("Scheduled for " + reminder.start().toString().replace('T', ' '));
            ButtonType open = new ButtonType("Open", ButtonBar.ButtonData.OK_DONE), snooze = new ButtonType("Snooze 10 min", ButtonBar.ButtonData.OTHER),
                    dismiss = new ButtonType("Dismiss", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().setAll(open, snooze, dismiss);
            dialog.setOnHidden(event -> {
                visibleReminders.remove(reminder.key());
                if (closingWorkspace || loadingWorkspace) return;
                com.crm.model.Task live = calendarController.tasksSnapshot().values().stream().flatMap(List::stream)
                        .filter(task -> task.getId().equals(reminder.task().getId())).findFirst().orElse(null);
                if (live == null) return;
                if (dialog.getResult() == snooze) live.snooze(reminder.date(), LocalDateTime.now().plusMinutes(10));
                else { live.acknowledgeReminder(reminder.date()); live.setSnoozedUntil(null); }
                handleDataChanged();
                if (dialog.getResult() == open) calendarController.editTask(reminder.date(), live);
            });
            dialog.show();
        }
    }

    private void initializeFocusedNotes() {
        HBox typography = (HBox) noteFontFamilyCombo.getParent();
        int index = noteEditorPane.getChildren().indexOf(typography);
        noteEditorPane.getChildren().removeAll(typography, notePreviewSettingsBar);
        TitledPane appearance = new TitledPane("Editor appearance", new VBox(8, typography, notePreviewSettingsBar)); appearance.setExpanded(false);
        notePreviewToggle.selectedProperty().addListener((o, before, preview) -> {
            typography.setVisible(!preview); typography.setManaged(!preview);
            appearance.setText(preview ? "Reading appearance" : "Editor appearance");
        });
        appearance.getStyleClass().add("note-appearance-disclosure"); noteEditorPane.getChildren().add(index, appearance);
        for (HBox bar : List.of(typography, notePreviewSettingsBar)) {
            var controls = new ArrayList<>(bar.getChildren()); bar.getChildren().clear();
            javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane(8, 8);
            controls.stream().filter(node -> node.getClass() != Region.class).forEach(flow.getChildren()::add);
            flow.prefWrapLengthProperty().bind(noteEditorPane.widthProperty().subtract(40));
            bar.getChildren().add(flow); HBox.setHgrow(flow, javafx.scene.layout.Priority.ALWAYS);
        }
        HBox metadataRow = (HBox) noteFolderCombo.getParent(); VBox header = (VBox) metadataRow.getParent();
        header.setMinHeight(Region.USE_PREF_SIZE);
        int rowIndex = header.getChildren().indexOf(metadataRow); var controls = new ArrayList<>(metadataRow.getChildren());
        metadataRow.getChildren().clear(); header.getChildren().remove(metadataRow);
        javafx.scene.layout.FlowPane links = new javafx.scene.layout.FlowPane(8, 8); links.getStyleClass().add("note-editor-link-row");
        links.setMinHeight(Region.USE_PREF_SIZE);
        controls.stream().filter(node -> node.getClass() != Region.class).forEach(links.getChildren()::add);
        ComboBox<Contact> contact = new ComboBox<>(); contact.setPromptText("Link a contact"); contact.setPrefWidth(215);
        contact.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Contact value) { return value == null ? "" : value.nameProperty().get(); }
            @Override public Contact fromString(String value) { return null; }
        });
        boolean[] updating = {false};
        contact.valueProperty().addListener((o, a, b) -> { if (!updating[0]) notesController.linkContact(b == null ? "" : b.getId()); });
        Button unlink = new Button("Clear contact"); unlink.getStyleClass().add("text-button"); unlink.setOnAction(e -> contact.setValue(null));
        links.getChildren().addAll(contact, unlink); header.getChildren().add(rowIndex, links);
        notesController.setOnNoteOpened(note -> {
            updating[0] = true;
            contact.getItems().setAll(contactsController.snapshot());
            contact.setValue(contact.getItems().stream().filter(item -> item.getId().equals(note.getContactId())).findFirst().orElse(null));
            updating[0] = false;
        });
        HBox titleRow = (HBox) noteTitleField.getParent(); ToggleButton focus = new ToggleButton("Focus"); focus.getStyleClass().add("btn-secondary");
        markdownToolbar.getChildren().remove(notePreviewToggle);
        titleRow.getChildren().add(titleRow.getChildren().size() - 1, notePreviewToggle);
        focus.setId("noteFocusButton");
        titleRow.getChildren().add(Math.max(0, titleRow.getChildren().size() - 1), focus);
        boolean[] priorSidebars = new boolean[2];
        focus.selectedProperty().addListener((o, a, enabled) -> {
            if (enabled) { priorSidebars[0] = leftSidebarWrapper.isManaged(); priorSidebars[1] = rightSidebarWrapper.isManaged(); setSidebarExpanded(true, false); setSidebarExpanded(false, false); }
            else { if (priorSidebars[0]) setSidebarExpanded(true, true); if (priorSidebars[1]) setSidebarExpanded(false, true); }
            setControlVisible(links, !enabled); setControlVisible(appearance, !enabled);
        });
        notesView.visibleProperty().addListener((o, a, visible) -> { if (!visible) focus.setSelected(false); });
        noteEditorPane.visibleProperty().addListener((o, a, visible) -> { if (!visible) focus.setSelected(false); });
    }

    private void installWorkspaceShortcuts(Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (!event.isShortcutDown() || event.getCode() != KeyCode.Z && event.getCode() != KeyCode.Y) return;
            for (Node node = scene.getFocusOwner(); node != null; node = node.getParent())
                if (node instanceof TextInputControl || node instanceof org.fxmisc.richtext.GenericStyledArea<?, ?, ?>) return;
            if (event.getCode() == KeyCode.Y || event.isShiftDown()) handleRedo(); else handleUndo();
            event.consume();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), this::handleWorkspaceSearch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::handleAddTask);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::handleHelp);
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> observeWindow(newWindow));
        observeWindow(scene.getWindow());
    }

    private void observeWindow(Window window) {
        clockRefresh.stop();
        if (observedWindow != null) observedWindow.showingProperty().removeListener(windowVisibilityListener);
        observedWindow = window;
        if (window != null) {
            window.showingProperty().addListener(windowVisibilityListener);
            if (window.isShowing()) clockRefresh.play();
        }
    }

    private void openSearchResult(WorkspaceSearchService.Result result) {
        switch (result.kind()) {
            case CONTACT -> {
                navigationController.showContacts();
                contactsController.openById(result.id());
            }
            case NOTE -> notesController.openById(result.id());
            case TASK -> calendarController.tasksSnapshot().values().stream().flatMap(List::stream)
                    .filter(task -> task.getId().equals(result.id())).findFirst()
                    .ifPresent(task -> calendarController.editTask(result.date(), task));
        }
    }

    private void refreshAttention(Map<LocalDate, List<com.crm.model.Task>> tasks) {
        long overdue = TaskScheduleService.overdueCount(tasks, LocalDateTime.now());
        boolean needsAttention = overdue > 0;
        homeFocusIcon.setIconLiteral(needsAttention ? "fas-exclamation-circle" : "far-check-circle");
        homeFocusSummary.pseudoClassStateChanged(PseudoClass.getPseudoClass("attention"), needsAttention);
        homeFocusTitleLabel.setText(needsAttention ? overdue + (overdue == 1 ? " task needs" : " tasks need") + " your attention"
                : "You're up to date");
        homeFocusDetailLabel.setText(needsAttention ? "Review overdue work and decide what comes next."
                : "No overdue tasks. Take a look at today's plan.");
        homeFocusButton.setText(needsAttention ? "Review tasks →" : "View today →");
        reminderCountLabel.setText(overdue > 99 ? "99+" : String.valueOf(overdue));
        setControlVisible(reminderCountLabel, needsAttention);
        remindersButton.setAccessibleText(needsAttention ? "Review " + overdue + " overdue tasks" : "Review today's tasks");
    }

    @FXML private void handleWorkspaceSearch() {
        workspaceSearchController.show();
    }

    @FXML private void handleUndo() {
        if (loadingWorkspace || closingWorkspace) return;
        noteChangeDebounce.stop(); saveCurrentData(); history.undo().ifPresent(this::restoreHistorySnapshot);
    }
    @FXML private void handleRedo() {
        if (loadingWorkspace || closingWorkspace) return;
        history.redo().ifPresent(this::restoreHistorySnapshot);
    }
    private void restoreHistorySnapshot(CrmDataSnapshot snapshot) {
        restoringHistory = true; loadingWorkspace = true;
        try { applyUserData(snapshot); }
        finally { loadingWorkspace = false; }
        try { saveCurrentData(); } finally { restoringHistory = false; }
    }
    @FXML private void handleTrash() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Recently deleted"); themeService.applyTo(dialog);
        VBox list = new VBox(10); list.setPrefWidth(510); list.getStyleClass().add("event-editor");
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
        list.getChildren().clear();
        if (trash.isEmpty()) list.getChildren().add(new Label("Your trash is empty. Deleted contacts, tasks and notes can be restored here."));
        for (Contact contact : trash.contacts()) addTrashRow(list, "Contact · " + contact.nameProperty().get(), () -> {
            var contacts = contactsController.snapshot();
            if (contacts.stream().noneMatch(item -> item.getId().equals(contact.getId()))) contacts.add(contact);
            contactsController.setContacts(contacts);
            trash = new CrmTrash(trash.contacts().stream().filter(item -> !item.getId().equals(contact.getId())).toList(), trash.tasks(), trash.notes());
            handleDataChanged(); refresh[0].run();
        });
        trash.tasks().forEach((date, items) -> items.forEach(task -> addTrashRow(list, "Task · " + task.getTitle(), () -> {
            Map<LocalDate, List<com.crm.model.Task>> tasks = calendarController.tasksSnapshot();
            boolean present = tasks.values().stream().flatMap(List::stream).anyMatch(item -> item.getId().equals(task.getId()));
            if (!present) tasks.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task.copy());
            WorkspaceHistoryService.restoreTaskLinks(task, notesController.snapshot());
            Map<LocalDate, List<com.crm.model.Task>> remaining = new HashMap<>();
            trash.tasks().forEach((day, entries) -> { var kept = entries.stream().filter(item -> !item.getId().equals(task.getId())).toList(); if (!kept.isEmpty()) remaining.put(day, kept); });
            trash = new CrmTrash(trash.contacts(), remaining, trash.notes());
            calendarController.applyState(tasks, calendarController.selectedDate(), calendarController.viewMode(), calendarController.zoom());
            handleDataChanged(); refresh[0].run();
        })));
        for (com.crm.model.Note note : trash.notes()) addTrashRow(list, "Note · " + note.getTitle(), () -> {
            var notes = notesController.snapshot();
            if (notes.stream().noneMatch(item -> item.getId().equals(note.getId()))) notes.add(note);
            notesController.applyState(notes, notesController.foldersSnapshot(), calendarController.tasksSnapshot());
            trash = new CrmTrash(trash.contacts(), trash.tasks(), trash.notes().stream().filter(item -> !item.getId().equals(note.getId())).toList());
            handleDataChanged(); refresh[0].run();
        });
        };
        refresh[0].run();
        ScrollPane scroll = new ScrollPane(list); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(420);
        dialog.getDialogPane().setContent(scroll); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.showAndWait();
    }
    private void addTrashRow(VBox list, String title, Runnable restore) {
        Label label = new Label(title); label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
        Button button = new Button("Restore"); button.getStyleClass().add("btn-secondary"); button.setOnAction(event -> restore.run());
        HBox row = new HBox(12, label, button); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); list.getChildren().add(row);
    }

    @FXML private void handleReviewTasks() {
        boolean overdue = TaskScheduleService.overdueCount(calendarController.tasksSnapshot(), LocalDateTime.now()) > 0;
        tasksController.showAttention(overdue);
        navigationController.showTasks();
    }

    @FXML private void handleHelp() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Make yourself at home");
        dialog.setHeaderText("A few shortcuts to keep work moving");
        themeService.applyTo(dialog);
        VBox content = new VBox(16);
        content.getStyleClass().add("workspace-help");
        addHelpItem(content, "Search your workspace", shortcutName() + " K",
                "Find a contact, task, or note. Search also looks inside note content and custom contact fields.");
        addHelpItem(content, "Create a task", shortcutName() + " Shift N",
                "Schedule an activity from any section. Calendar and Tasks share the same information.");
        addHelpItem(content, "Stay on top of your day", "Bell icon",
                "Review overdue work, or see today's tasks when nothing is overdue. Open records directly from Home.");
        addHelpItem(content, "Keep a copy of your work", "Settings → Export data",
                "Your changes save automatically on this device. Export your workspace to keep a portable copy.");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    private static void addHelpItem(VBox container, String title, String shortcut, String detail) {
        Label heading = new Label(title + "  ·  " + shortcut);
        heading.getStyleClass().add("overview-card-title");
        heading.setWrapText(true);
        Label description = new Label(detail);
        description.getStyleClass().add("section-subtitle");
        description.setWrapText(true);
        container.getChildren().add(new VBox(5, heading, description));
    }

    private static String shortcutName() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac") ? "⌘" : "Ctrl";
    }

    private static void setControlVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void initializeResizableSidebars() {
        setSidebarWidth(leftPanel, leftExpandedWidth);
        setSidebarWidth(rightPanel, rightExpandedWidth);
        installResizeBehavior(leftResizeHandle, leftPanel, true);
        installResizeBehavior(rightResizeHandle, rightPanel, false);
        appShell.widthProperty().addListener((observable, oldWidth, newWidth) ->
                handleShellWidthChanged(oldWidth.doubleValue(), newWidth.doubleValue()));
        updateSidebarToggleAccessibility();
    }

    private void installResizeBehavior(Region handle, VBox panel, boolean leftSidebar) {
        SidebarDragState drag = new SidebarDragState();
        handle.setCursor(Cursor.H_RESIZE);
        handle.setOnMousePressed(event -> {
            drag.startScreenX = event.getScreenX();
            drag.startWidth = panel.getWidth();
            drag.requestedWidth = drag.startWidth;
            drag.clip = new Rectangle();
            drag.clip.widthProperty().bind(panel.widthProperty());
            drag.clip.heightProperty().bind(panel.heightProperty());
            panel.setClip(drag.clip);
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            double horizontalMovement = event.getScreenX() - drag.startScreenX;
            drag.requestedWidth = drag.startWidth + (leftSidebar ? horizontalMovement : -horizontalMovement);
            double previewWidth = clamp(drag.requestedWidth, 0, maximumSidebarWidth(leftSidebar));
            setSidebarWidth(panel, previewWidth);
            event.consume();
        });
        handle.setOnMouseReleased(event -> {
            panel.setClip(null);
            if (drag.clip != null) {
                drag.clip.widthProperty().unbind();
                drag.clip.heightProperty().unbind();
                drag.clip = null;
            }
            if (drag.requestedWidth <= SIDEBAR_COLLAPSE_THRESHOLD) {
                setSidebarExpanded(leftSidebar, false);
            } else {
                double settledWidth = clamp(drag.requestedWidth,
                        minimumSidebarWidth(leftSidebar), maximumSidebarWidth(leftSidebar));
                rememberExpandedWidth(leftSidebar, settledWidth);
                setSidebarWidth(panel, settledWidth);
            }
            event.consume();
        });
        handle.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                setSidebarExpanded(leftSidebar, false);
                event.consume();
            }
        });
    }

    private void setSidebarExpanded(boolean leftSidebar, boolean expanded) {
        setSidebarExpanded(leftSidebar, expanded, false);
        if (expanded) fitSidebarsToWidth(appShell.getWidth(), leftSidebar);
    }

    private void setSidebarExpanded(boolean leftSidebar, boolean expanded, boolean automatic) {
        HBox wrapper = leftSidebar ? leftSidebarWrapper : rightSidebarWrapper;
        VBox panel = leftSidebar ? leftPanel : rightPanel;
        if (expanded) {
            double rememberedWidth = leftSidebar ? leftExpandedWidth : rightExpandedWidth;
            setSidebarWidth(panel, clamp(rememberedWidth,
                    minimumSidebarWidth(leftSidebar), maximumSidebarWidth(leftSidebar)));
        }
        wrapper.setManaged(expanded);
        wrapper.setVisible(expanded);
        setAutomaticallyCollapsed(leftSidebar, automatic && !expanded);
        updateSidebarToggleAccessibility();
    }

    private void handleShellWidthChanged(double oldWidth, double newWidth) {
        if (newWidth <= 0 || adjustingResponsiveSidebars) return;
        if (newWidth < oldWidth || workspaceDeficit(newWidth) > 0) {
            fitSidebarsToWidth(newWidth, null);
        } else if (newWidth > oldWidth) {
            restoreResponsiveSidebars(newWidth);
        }
    }

    /** Shrinks panels before hiding them, preserving the central workspace while the window narrows. */
    private void fitSidebarsToWidth(double shellWidth, Boolean preferredSidebar) {
        if (shellWidth <= 0 || adjustingResponsiveSidebars) return;
        adjustingResponsiveSidebars = true;
        try {
            boolean first = preferredSidebar == null ? false : !preferredSidebar;
            boolean second = !first;
            double deficit = workspaceDeficit(shellWidth);
            deficit = shrinkSidebar(first, deficit);
            deficit = shrinkSidebar(second, deficit);

            if (deficit > 0 && sidebarWrapper(first).isManaged()) {
                setSidebarExpanded(first, false, true);
                deficit = workspaceDeficit(shellWidth);
            }
            if (deficit > 0 && sidebarWrapper(second).isManaged()) {
                setSidebarExpanded(second, false, true);
            }
        } finally {
            adjustingResponsiveSidebars = false;
        }
    }

    /** Reopens only automatically hidden panels and progressively restores the user's chosen widths. */
    private void restoreResponsiveSidebars(double shellWidth) {
        if (adjustingResponsiveSidebars) return;
        adjustingResponsiveSidebars = true;
        try {
            restoreAutomaticallyCollapsedSidebar(true, shellWidth);
            restoreAutomaticallyCollapsedSidebar(false, shellWidth);
            if (!leftAutomaticallyCollapsed && !rightAutomaticallyCollapsed) {
                restoreRememberedWidths(shellWidth);
            }
        } finally {
            adjustingResponsiveSidebars = false;
        }
    }

    private void restoreAutomaticallyCollapsedSidebar(boolean leftSidebar, double shellWidth) {
        boolean automaticallyCollapsed = leftSidebar ? leftAutomaticallyCollapsed : rightAutomaticallyCollapsed;
        if (!automaticallyCollapsed) return;
        double requiredWidth = minimumSidebarWidth(leftSidebar) + RESIZE_HANDLE_WIDTH;
        double availableWidth = shellWidth - MIN_WORKSPACE_WIDTH - totalSidebarWidth();
        if (availableWidth >= requiredWidth) setSidebarExpanded(leftSidebar, true, true);
    }

    private void restoreRememberedWidths(double shellWidth) {
        double availableExtra = shellWidth - MIN_WORKSPACE_WIDTH - totalSidebarWidth();
        if (availableExtra <= 0) return;

        double leftNeed = widthToRestore(true);
        double rightNeed = widthToRestore(false);
        double totalNeed = leftNeed + rightNeed;
        if (totalNeed <= 0) return;

        double usableExtra = Math.min(availableExtra, totalNeed);
        double leftExtra = usableExtra * leftNeed / totalNeed;
        double rightExtra = usableExtra - leftExtra;
        if (leftNeed > 0) setSidebarWidth(leftPanel, leftPanel.getPrefWidth() + leftExtra);
        if (rightNeed > 0) setSidebarWidth(rightPanel, rightPanel.getPrefWidth() + rightExtra);
    }

    private double widthToRestore(boolean leftSidebar) {
        if (!sidebarWrapper(leftSidebar).isManaged()) return 0;
        double rememberedWidth = leftSidebar ? leftExpandedWidth : rightExpandedWidth;
        VBox panel = leftSidebar ? leftPanel : rightPanel;
        return Math.max(0, rememberedWidth - panel.getPrefWidth());
    }

    private double shrinkSidebar(boolean leftSidebar, double deficit) {
        if (deficit <= 0 || !sidebarWrapper(leftSidebar).isManaged()) return deficit;
        VBox panel = leftSidebar ? leftPanel : rightPanel;
        double currentWidth = panel.getPrefWidth();
        double reduction = Math.min(deficit, Math.max(0, currentWidth - minimumSidebarWidth(leftSidebar)));
        if (reduction > 0) setSidebarWidth(panel, currentWidth - reduction);
        return deficit - reduction;
    }

    private double workspaceDeficit(double shellWidth) {
        return Math.max(0, MIN_WORKSPACE_WIDTH + totalSidebarWidth() - shellWidth);
    }

    private double totalSidebarWidth() {
        return sidebarWidth(true) + sidebarWidth(false);
    }

    private double sidebarWidth(boolean leftSidebar) {
        if (!sidebarWrapper(leftSidebar).isManaged()) return 0;
        VBox panel = leftSidebar ? leftPanel : rightPanel;
        return panel.getPrefWidth() + RESIZE_HANDLE_WIDTH;
    }

    private HBox sidebarWrapper(boolean leftSidebar) {
        return leftSidebar ? leftSidebarWrapper : rightSidebarWrapper;
    }

    private void setAutomaticallyCollapsed(boolean leftSidebar, boolean automaticallyCollapsed) {
        if (leftSidebar) leftAutomaticallyCollapsed = automaticallyCollapsed;
        else rightAutomaticallyCollapsed = automaticallyCollapsed;
    }

    private void updateSidebarToggleAccessibility() {
        updateToggleAccessibility(leftSidebarToggleButton, leftSidebarWrapper.isManaged(), "navigation");
        updateToggleAccessibility(agendaToggleButton, rightSidebarWrapper.isManaged(), "agenda");
    }

    private void updateToggleAccessibility(Button button, boolean expanded, String panelName) {
        String description = (expanded ? "Hide " : "Show ") + panelName;
        button.setAccessibleText(description);
        if (button.getTooltip() != null) button.getTooltip().setText(description);
    }

    private double maximumSidebarWidth(boolean leftSidebar) {
        double configuredMaximum = leftSidebar ? LEFT_SIDEBAR_MAX_WIDTH : RIGHT_SIDEBAR_MAX_WIDTH;
        double shellWidth = appShell.getWidth();
        if (shellWidth <= 0) return configuredMaximum;
        double otherWidth = sidebarWidth(!leftSidebar);
        double available = shellWidth - otherWidth - MIN_WORKSPACE_WIDTH - RESIZE_HANDLE_WIDTH;
        return Math.max(minimumSidebarWidth(leftSidebar), Math.min(configuredMaximum, available));
    }

    private double minimumSidebarWidth(boolean leftSidebar) {
        return leftSidebar ? LEFT_SIDEBAR_MIN_WIDTH : RIGHT_SIDEBAR_MIN_WIDTH;
    }

    private void rememberExpandedWidth(boolean leftSidebar, double width) {
        if (leftSidebar) leftExpandedWidth = width;
        else rightExpandedWidth = width;
    }

    private void setSidebarWidth(VBox panel, double width) {
        panel.setMinWidth(width);
        panel.setPrefWidth(width);
        panel.setMaxWidth(width);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class SidebarDragState {
        private double startScreenX;
        private double startWidth;
        private double requestedWidth;
        private Rectangle clip;
    }

    @FXML
    private void handleThemeToggle() {
        themeService.toggle();
        applyActiveTheme();
    }

    private void applyTheme(ThemeService.Theme theme) {
        if (themeService.activeTheme() == theme) return;
        themeService.setTheme(theme);
        applyActiveTheme();
    }

    private void applyActiveTheme() {
        themeService.applyTo(themeToggleBtn.getScene());
        updateThemeButton();
        calendarController.refreshTheme();
        notesController.refreshTheme();
        if (currentUser == null) return;
        currentUser.setPreferredTheme(themeService.activeTheme().name());
        try {
            userRepository.save(currentUser);
        } catch (IllegalStateException failure) {
            dialogService.showError("Theme not saved",
                    "The theme was applied, but it could not be remembered for the next launch.");
        }
    }

    private void updateThemeButton() {
        themeToggleIcon.setIconLiteral(themeService.isBlueGrayTheme() || themeService.isGrayBlueTheme()
                ? "fas-palette"
                : themeService.isDarkMode() ? "fas-sun" : "fas-moon");
        themeToggleBtn.setText("Theme: " + themeService.activeTheme().displayName());
        updateThemeCard(settingsDarkThemeBtn, settingsDarkThemeStatus, ThemeService.Theme.DARK);
        updateThemeCard(settingsLightThemeBtn, settingsLightThemeStatus, ThemeService.Theme.LIGHT);
        updateThemeCard(settingsBlueGrayThemeBtn, settingsBlueGrayThemeStatus, ThemeService.Theme.BLUE_GRAY);
        updateThemeCard(settingsGrayBlueThemeBtn, settingsGrayBlueThemeStatus, ThemeService.Theme.GRAY_BLUE);
    }

    private void updateThemeCard(Button card, Label status, ThemeService.Theme theme) {
        boolean selected = themeService.activeTheme() == theme;
        card.getStyleClass().remove("settings-theme-active");
        if (selected) card.getStyleClass().add("settings-theme-active");
        status.setText(selected ? "Active" : "Use theme");
    }

    private void bindSettingsAccountPresentation() {
        settingsAccountNameLabel.textProperty().bind(currentUserLabel.textProperty());
        settingsAvatarImage.imageProperty().bind(avatarImage.imageProperty());
        settingsAvatarImage.visibleProperty().bind(avatarImage.visibleProperty());
        settingsAvatarImage.managedProperty().bind(avatarImage.managedProperty());
        settingsDefaultAvatarIcon.visibleProperty().bind(defaultAvatarIcon.visibleProperty());
        settingsDefaultAvatarIcon.managedProperty().bind(defaultAvatarIcon.managedProperty());
    }
}
