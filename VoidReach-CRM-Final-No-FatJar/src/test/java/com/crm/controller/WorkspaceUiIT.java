package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.Note;
import com.crm.model.NoteFormat;
import com.crm.model.Task;
import com.crm.service.ThemeService;
import javafx.application.Platform;
import javafx.css.CssParser;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Optional desktop smoke test: mvn -Dtest=WorkspaceUiIT test. Uses only synthetic data. */
class WorkspaceUiIT {
    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            com.crm.service.Typography.load();
            ready.countDown();
        });
        assertTrue(ready.await(20, TimeUnit.SECONDS));
    }

    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void markdownReadingViewIsFaithfulAndThemeIndependent() throws Exception {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(Path.of("target", "ui-preview", "markdown-home").toAbsolutePath()).toString());
        javafx.stage.Stage[] window = new javafx.stage.Stage[1];
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
            Note note = new Note("A more considered workspace", NoteFormat.MARKDOWN);
            note.setContent("""
                    # A clearer direction

                    Good notes need **hierarchy**, comfortable spacing, and *room to think*.
                    This is still the same paragraph, not an artificial new block.

                    ## What matters this week

                    1. Bring the reading experience into focus.
                       - Make the next step easy to find.
                       - Keep **detail and emphasis** in proportion.
                    2. Build a consistent visual language.

                    | Area | Decision |
                    | :--- | :--- |
                    | Interface | Inter, with a restrained type scale |
                    | Code | JetBrains Mono |
                    | Reading | Independent appearance settings |

                    ### Implementation notes

                    ```java
                    if (notes.size() > 0) {
                        return "A clearer direction";
                    }
                    ```

                    > Good design makes the important things easier to read.

                    - [x] Review headings and paragraphs
                    - [ ] Share the final notes

                    [Project website](https://example.test/preview)
                    [[Linked note]]
                    ![Reference image](https://example.test/image.png)
                    """);
            String originalSource = note.getContent();
            note.setFontWeight(700); note.setItalic(true);
            Note linked = new Note("Linked note", NoteFormat.TEXT);
            java.util.List<String> opened = new java.util.ArrayList<>();
            com.crm.service.ExternalLinks.setOpener(opened::add);
            Parent root = fx(() -> {
                Parent loaded = loader.load();
                window[0] = new javafx.stage.Stage(); window[0].setScene(new Scene(loaded, 1280, 900)); window[0].show();
                var notes = (NotesController) field(loader.getController(), "notesController");
                notes.applyState(List.of(note, linked), List.of(), Map.of());
                notes.openById(note.getId());
                ((javafx.scene.control.ToggleButton) loaded.lookup("#notePreviewToggle")).fire();
                return loaded;
            });
            var web = fx(() -> (javafx.scene.web.WebView) root.lookup("#markdownWebView"));
            awaitFx(() -> web.getEngine().getDocument() != null && web.getEngine().getDocument().getElementById("article") != null
                    && web.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED);
            fx(() -> {
                assertFalse(web.getEngine().isJavaScriptEnabled());
                var doc = web.getEngine().getDocument();
                assertEquals(1, doc.getElementsByTagName("table").getLength());
                assertEquals(0, doc.getElementsByTagName("img").getLength(), "No remote image is fetched while reading");
                assertEquals("if (notes.size() > 0) {\n    return \"A clearer direction\";\n}\n", doc.getElementById("code-1").getTextContent());
                domClick(doc, (org.w3c.dom.Element) doc.getElementsByTagName("button").item(0));
                assertEquals(doc.getElementById("code-1").getTextContent(), javafx.scene.input.Clipboard.getSystemClipboard().getString());
                domClick(doc, (org.w3c.dom.Element) doc.getElementsByTagName("a").item(0));
                assertEquals(List.of("https://example.test/preview"), opened);
                var size = (javafx.scene.control.ComboBox<Double>) root.lookup("#notePreviewFontSizeCombo");
                size.setValue(20.0);
                assertEquals("", note.getPreviewTextColor(), "Changing size must not pin the current theme's text color");
                assertTrue(doc.getElementById("reading-style").getTextContent().contains("--reading-size:20.0px"));
                size.setValue(18.0);
                assertTrue(root.lookup("#markdownToolbar").lookupAll(".markdown-tool").stream().allMatch(Node::isDisabled));
                assertEquals("Inter SemiBold", ((Label) root.lookup(".brand-title")).getFont().getName());
                assertEquals("Inter SemiBold", javafx.scene.text.Font.font(com.crm.service.Typography.editorFamily("System", false, 600), 14).getName());
                assertEquals("JetBrains Mono SemiBold", javafx.scene.text.Font.font(com.crm.service.Typography.editorFamily("System", true, 600), 14).getName());
                assertEquals("400", web.getEngine().executeScript("getComputedStyle(document.body).fontWeight"));
                assertEquals("normal", web.getEngine().executeScript("getComputedStyle(document.body).fontStyle"));
                assertTrue(web.getEngine().executeScript("getComputedStyle(document.querySelector('h1')).fontFamily").toString().contains("Inter SemiBold"));
                web.requestFocus();
                web.getEngine().executeScript("var selection = window.getSelection(); var range = document.createRange(); range.selectNodeContents(document.querySelector('h1')); selection.removeAllRanges(); selection.addRange(range);");
                assertEquals("A clearer direction", web.getEngine().executeScript("window.getSelection().toString()"));
                Event.fireEvent(web, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.C, false, true, false, false));
                assertEquals("A clearer direction", javafx.scene.input.Clipboard.getSystemClipboard().getString());
                web.getEngine().executeScript("window.getSelection().removeAllRanges()");
                return null;
            });
            for (ThemeService.Theme theme : ThemeService.Theme.values()) {
                fx(() -> {
                    invoke(loader.getController(), "applyTheme", new Class<?>[]{ThemeService.Theme.class}, theme);
                    root.applyCss(); root.layout(); return null;
                });
                Thread.sleep(180);
                fx(() -> { capture(root, Path.of("target", "ui-preview", "markdown-" + theme.name().toLowerCase() + ".png")); return null; });
            }
            fx(() -> {
                invoke(loader.getController(), "applyTheme", new Class<?>[]{ThemeService.Theme.class}, ThemeService.Theme.DARK);
                web.getEngine().executeScript("window.scrollTo(0, document.body.scrollHeight)");
                return null;
            });
            Thread.sleep(180);
            fx(() -> {
                capture(root, Path.of("target", "ui-preview", "markdown-code.png"));
                double scroll = ((Number) web.getEngine().executeScript("window.scrollY")).doubleValue();
                invoke(loader.getController(), "applyTheme", new Class<?>[]{ThemeService.Theme.class}, ThemeService.Theme.LIGHT);
                assertEquals(scroll, ((Number) web.getEngine().executeScript("window.scrollY")).doubleValue(), .5, "Theme changes preserve reading position");
                window[0].setWidth(840); window[0].setHeight(760);
                ((javafx.scene.control.TitledPane) root.lookup(".note-appearance-disclosure")).setAnimated(false);
                ((javafx.scene.control.TitledPane) root.lookup(".note-appearance-disclosure")).setExpanded(true);
                web.getEngine().executeScript("window.scrollTo(0, 0)");
                root.applyCss(); root.layout();
                var headerBounds = root.lookup(".note-editor-header").localToScene(root.lookup(".note-editor-header").getBoundsInLocal());
                var appearanceBounds = root.lookup(".note-appearance-disclosure").localToScene(root.lookup(".note-appearance-disclosure").getBoundsInLocal());
                assertTrue(headerBounds.getMaxY() <= appearanceBounds.getMinY(), "Wrapped metadata must not overlap appearance controls");
                assertInside(root.lookup("#notePreviewToggle"), root, "Edit/Preview remains available on compact windows");
                return null;
            });
            Thread.sleep(180);
            fx(() -> { capture(root, Path.of("target", "ui-preview", "markdown-compact.png")); return null; });
            fx(() -> {
                var doc = web.getEngine().getDocument();
                ((javafx.scene.control.ColorPicker) root.lookup("#notePreviewColorPicker")).setValue(javafx.scene.paint.Color.web("#123456"));
                invoke(loader.getController(), "applyTheme", new Class<?>[]{ThemeService.Theme.class}, ThemeService.Theme.DARK);
                invoke(loader.getController(), "applyTheme", new Class<?>[]{ThemeService.Theme.class}, ThemeService.Theme.LIGHT);
                assertTrue(doc.getElementById("reading-style").getTextContent().contains("--paper:#ffffff"));
                assertTrue(doc.getElementById("reading-style").getTextContent().contains("--ink:#123456"));
                note.setPreviewTextColor("");
                var preview = (javafx.scene.control.ToggleButton) root.lookup("#notePreviewToggle");
                preview.fire();
                assertEquals(originalSource, note.getContent(), "Reading never mutates the source");
                assertEquals(700, note.getFontWeight()); assertTrue(note.isItalic());
                assertTrue(root.lookup("#markdownToolbar").lookupAll(".markdown-tool").stream().noneMatch(Node::isDisabled));
                preview.fire();
                domClick(doc, (org.w3c.dom.Element) doc.getElementsByTagName("a").item(1));
                assertEquals(linked.getId(), ((NotesController) field(loader.getController(), "notesController")).currentNote().getId());
                assertTrue(root.lookup("#noteContentArea").isVisible(), "Wiki link returns to the target editor");
                return null;
            });
        } finally {
            fx(() -> { if (window[0] != null) window[0].hide(); return null; });
            com.crm.service.ExternalLinks.setOpener(address -> { });
            System.setProperty("user.home", originalHome);
        }
    }

    private static void domClick(org.w3c.dom.Document doc, org.w3c.dom.Element element) {
        var event = ((org.w3c.dom.events.DocumentEvent) doc).createEvent("MouseEvent");
        event.initEvent("click", true, true);
        ((org.w3c.dom.events.EventTarget) element).dispatchEvent(event);
    }

    @Test void previewHandlesEmptyLargeNotesAndStaleResultsWithoutLosingSource() throws Exception {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(Path.of("target", "ui-preview", "preview-boundary-home").toAbsolutePath()).toString());
        com.crm.view.MarkdownPreviewView[] view = new com.crm.view.MarkdownPreviewView[1];
        javafx.stage.Stage[] window = new javafx.stage.Stage[1];
        Note note = new Note("Preview boundary cases", NoteFormat.MARKDOWN);
        try {
            fx(() -> {
                view[0] = new com.crm.view.MarkdownPreviewView(address -> { });
                window[0] = new javafx.stage.Stage(); window[0].setScene(new Scene(view[0], 800, 600));
                new ThemeService(() -> null).applyTo(window[0].getScene()); window[0].show();
                view[0].show(note, ThemeService.Theme.DARK); return null;
            });
            var web = fx(() -> (javafx.scene.web.WebView) view[0].lookup("#markdownWebView"));
            awaitFx(() -> web.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED);
            fx(() -> {
                assertTrue(web.getEngine().getDocument().getElementById("article").getTextContent().contains("This note is empty"));
                note.setContent("# Superseded"); view[0].show(note, ThemeService.Theme.DARK);
                note.setContent("# Latest revision"); view[0].show(note, ThemeService.Theme.DARK); return null;
            });
            awaitFx(() -> web.getEngine().getDocument() != null && web.getEngine().getDocument().getElementById("article") != null
                    && web.getEngine().getDocument().getElementById("article").getTextContent().contains("Latest revision"));
            fx(() -> {
                note.setContent("x".repeat(2_000_001)); view[0].show(note, ThemeService.Theme.DARK); return null;
            });
            awaitFx(() -> ((Label) view[0].lookup(".reading-status")).getText().startsWith("Preview unavailable."));
            fx(() -> {
                assertEquals(2_000_001, note.getContent().length());
                assertTrue(((Button) view[0].lookup(".button")).isDisabled(), "Failure must not expose stale text for copying");
                assertTrue(web.getEngine().getDocument().getElementById("reading-style").getTextContent().contains("--paper:#202630"));
                note.setContent("Readable again"); view[0].show(note, ThemeService.Theme.DARK); return null;
            });
            awaitFx(() -> ((Label) view[0].lookup(".reading-status")).getText().equals("Reading view"));
            fx(() -> { assertFalse(((Button) view[0].lookup(".button")).isDisabled()); return null; });
        } finally {
            fx(() -> { if (window[0] != null) window[0].hide(); return null; });
            System.setProperty("user.home", originalHome);
        }
    }

    @Test void calendarMouseDragAndResizeUseTheSelectedSnap() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path isolated = Files.createDirectories(Path.of("target", "ui-preview", "drag-home").toAbsolutePath());
        System.setProperty("user.home", isolated.toString());
        try {
            fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
                Parent root = loader.load(); new Scene(root, 1280, 840); root.resize(1280, 840);
                CalendarController calendar = (CalendarController) field(loader.getController(), "calendarController");
                ((Button) root.lookup("#sideCalendarBtn")).fire();
                for (var option : com.crm.model.CalendarPreferences.Snap.values()) for (boolean resize : List.of(false, true)) {
                    Task task = Task.scheduled("drag-test", "Off-grid event", "", 607, 60, "Blue", false);
                    calendar.setSnap(option); calendar.applyState(Map.of(LocalDate.now(), List.of(task)), LocalDate.now(), "Day", 1);
                    root.applyCss(); root.layout();
                    Node card = root.lookup(".calendar-event-v2"); double y = resize ? ((Region) card).getHeight() - 2 : 10;
                    var press = pointer(card, javafx.scene.input.MouseEvent.MOUSE_PRESSED, y, false);
                    var drag = pointer(card, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, y + 4, false);
                    var release = pointer(card, javafx.scene.input.MouseEvent.MOUSE_RELEASED, y + 4, false);
                    assertEquals(y, press.getY(), 0.01, "Synthetic pointer must use card-local coordinates");
                    card.getOnMousePressed().handle(press); card.getOnMouseDragged().handle(drag); card.getOnMouseReleased().handle(release);
                    Task saved = calendar.tasksSnapshot().get(LocalDate.now()).getFirst();
                    assertEquals(resize ? 607 : calendar.preferences().snapStart(611, false), saved.getStartMin(), option.toString());
                    assertEquals(resize ? calendar.preferences().snapDuration(607, 60, 4, false) : 60, saved.getDuration(), option.toString());
                }
                Task fine = Task.scheduled("drag-test", "Fine movement", "", 607, 60, "Blue", false);
                calendar.setSnap(com.crm.model.CalendarPreferences.Snap.GRID);
                calendar.applyState(Map.of(LocalDate.now(), List.of(fine)), LocalDate.now(), "Day", 1); root.applyCss(); root.layout();
                Node card = root.lookup(".calendar-event-v2");
                var press = pointer(card, javafx.scene.input.MouseEvent.MOUSE_PRESSED, 10, true);
                var drag = pointer(card, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, 14, true);
                var fineDrag = pointer(card, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, 11, true);
                var release = pointer(card, javafx.scene.input.MouseEvent.MOUSE_RELEASED, 11, true);
                card.getOnMousePressed().handle(press); card.getOnMouseDragged().handle(drag);
                card.getOnMouseDragged().handle(fineDrag); card.getOnMouseReleased().handle(release);
                assertEquals(608, calendar.tasksSnapshot().get(LocalDate.now()).getFirst().getStartMin(), "Alt and fine movement must work inside the original drag threshold");
                return null;
            });
        } finally { System.setProperty("user.home", originalHome); }
    }

    private static javafx.scene.input.MouseEvent pointer(Node node, javafx.event.EventType<javafx.scene.input.MouseEvent> type, double y, boolean alt) {
        var scene = node.localToScene(10, y);
        return new javafx.scene.input.MouseEvent(null, null, type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                javafx.scene.input.MouseButton.PRIMARY, 1, false, false, alt, false,
                type != javafx.scene.input.MouseEvent.MOUSE_RELEASED, false, false, false, false, true,
                new javafx.scene.input.PickResult(node, new javafx.geometry.Point3D(10, y, 0), 0)).copyFor(node, node);
    }

    @Test void loginRecoveryAndSplashLoadWithTheNewBrand() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path isolated = Files.createDirectories(Path.of("target", "ui-preview", "login-home").toAbsolutePath());
        System.setProperty("user.home", isolated.toString());
        try {
            fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/LoginView.fxml"));
                Parent root = loader.load(); LoginController login = loader.getController();
                Scene scene = new Scene(root, 580, 660);
                scene.getStylesheets().add(getClass().getResource("/css/style-dark.css").toExternalForm());
                scene.getStylesheets().add(getClass().getResource("/css/typography.css").toExternalForm());
                root.resize(580, 660);
                for (String view : List.of("showLogin", "showRegister", "showRecovery")) {
                    invoke(login, view, new Class<?>[0]); root.applyCss(); root.layout();
                    capture(root, Path.of("target", "ui-preview", view + ".png"));
                    Node card = root.lookup(".auth-card"); var bounds = card.localToScene(card.getLayoutBounds());
                    assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 661, "Authentication card must fit: " + view + " " + bounds);
                }
                invoke(login, "handleRecovery", new Class<?>[0]);
                assertFalse(((Label) root.lookup("#recoveryMessage")).getText().isBlank());
                ((TextField) root.lookup("#recoveryEmail")).setText("test@example.test");
                invoke(login, "handleRecovery", new Class<?>[0]); root.applyCss(); root.layout();
                assertTrue(root.lookup("#resetPane").isManaged());
                assertEquals("test@example.test", ((TextField) root.lookup("#resetEmail")).getText());
                assertTrue(((TextField) root.lookup("#resetCode")).getPromptText().contains("recovery key"));
                login.resetForLoginScreen(); root.applyCss(); root.layout();
                assertTrue(root.lookup("#loginPane").isManaged());
                assertTrue(((TextField) root.lookup("#resetEmail")).getText().isEmpty());
                capture(root, Path.of("target", "ui-preview", "login.png"));
                FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/com/crm/view/SplashScreen.fxml"));
                Parent splash = splashLoader.load();
                SplashScreenController startup = splashLoader.getController(); startup.setStatus("Ready"); startup.setProgress(1);
                assertInstanceOf(com.crm.view.BrandMark.class, splash.lookup("#logoView"));
                assertEquals("100%", ((Label) splash.lookup("#percentLabel")).getText());
                return null;
            });
        } finally { System.setProperty("user.home", originalHome); }
    }

    @Test void calendarFlowsPreserveDraftsLinksAndUndoInAShownWindow() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path isolated = Files.createDirectories(Path.of("target", "ui-preview", "calendar-flow-home").toAbsolutePath());
        System.setProperty("user.home", isolated.toString());
        javafx.stage.Stage[] window = new javafx.stage.Stage[1]; MainController[] main = new MainController[1];
        try {
            var user = new com.crm.service.AuthService(new com.crm.repository.LocalUserRepository())
                    .register("Calendar Tester", java.util.UUID.randomUUID() + "@example.test", "isolated-test-password");
            var repository = new com.crm.repository.LocalCrmDataRepository();
            CrmDataSnapshot demo = demoWorkspace(); repository.saveForUser(user.getId(), demo);
            Parent root = fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml")); Parent loaded = loader.load();
                main[0] = loader.getController(); window[0] = new javafx.stage.Stage(); window[0].setTitle("VoidReach · isolated UI verification");
                window[0].setScene(new Scene(loaded, 1280, 840)); window[0].show(); main[0].setCurrentUser(user, () -> { }); return loaded;
            });
            awaitFx(() -> !(boolean) field(main[0], "loadingWorkspace"));
            CalendarController calendar = fx(() -> (CalendarController) field(main[0], "calendarController"));
            LocalDate today = LocalDate.now();
            fx(() -> {
                for (com.crm.model.AppIcon icon : com.crm.model.AppIcon.values()) {
                    ((Button) root.lookup("#accountMenuButton")).fire();
                    var popup = Window.getWindows().stream().filter(javafx.scene.control.ContextMenu.class::isInstance)
                            .map(javafx.scene.control.ContextMenu.class::cast)
                            .filter(menu -> menu.getItems().stream().anyMatch(item -> "appIconMenu".equals(item.getId()))).findFirst().orElseThrow();
                    var choices = (javafx.scene.control.Menu) popup.getItems().stream().filter(item -> "appIconMenu".equals(item.getId())).findFirst().orElseThrow();
                    choices.getItems().stream().filter(item -> ("appIcon" + icon.name()).equals(item.getId())).findFirst().orElseThrow().fire();
                    popup.hide(); root.applyCss(); root.layout();
                    assertEquals(icon, com.crm.view.BrandMark.selectedIcon());
                    assertEquals(icon.name(), new com.crm.repository.LocalUserRepository().findByEmail(user.getEmail()).orElseThrow().getPreferredIcon());
                    assertEquals(com.crm.view.BrandMark.icon(icon, 32).getPixelReader().getArgb(16, 16), window[0].getIcons().getFirst().getPixelReader().getArgb(16, 16));
                    var mark = root.lookupAll("*").stream().filter(com.crm.view.BrandMark.class::isInstance).findFirst().orElseThrow();
                    assertTrue(mark.getAccessibleText().contains(icon.toString()));
                    if (icon == com.crm.model.AppIcon.BOOK) capture(root, Path.of("target", "ui-preview", "book-icon-workspace.png"));
                }
                return null;
            });
            fx(() -> {
                ((Button) root.lookup("#sideCalendarBtn")).fire();
                ((javafx.scene.control.ComboBox<String>) root.lookup("#viewModeCombo")).setValue("Day"); root.applyCss(); root.layout();
                var snapMenu = (javafx.scene.control.MenuButton) root.lookup("#calendarSnapMenu");
                for (com.crm.model.CalendarPreferences.Snap option : com.crm.model.CalendarPreferences.Snap.values()) {
                    snapMenu.getItems().stream().filter(item -> item.getUserData() == option).findFirst().orElseThrow().fire();
                    assertEquals(option, calendar.preferences().snapMode());
                    assertTrue(snapMenu.getText().endsWith(option.toString()));
                }
                calendar.setSnap(com.crm.model.CalendarPreferences.Snap.TEN);
                root.applyCss(); root.layout(); capture(root, Path.of("target", "ui-preview", "calendar-snap.png"));
                var events = root.lookupAll(".calendar-event-v2").stream().filter(node -> node.getAccessibleText().startsWith("Northstar proposal review") ||
                        node.getAccessibleText().startsWith("Design handoff")).toList();
                assertEquals(2, events.size());
                var one = events.get(0).getBoundsInParent(); var two = events.get(1).getBoundsInParent();
                assertTrue(one.getMaxX() <= two.getMinX() || two.getMaxX() <= one.getMinX(), "Overlapping events must use separate columns");
                var scroll = (javafx.scene.control.ScrollPane) root.lookup("#calendarScrollPane");
                Node header = root.lookup(".calendar-fixed-headers"); double headerY = header.localToScene(0, 0).getY();
                scroll.setVvalue(0.7); root.layout(); assertEquals(headerY, header.localToScene(0, 0).getY(), 0.1);
                return null;
            });
            for (ThemeService.Theme theme : ThemeService.Theme.values()) for (String mode : List.of("Day", "Week", "Month", "Agenda")) {
                fx(() -> {
                    invoke(main[0], "applyTheme", new Class<?>[]{ThemeService.Theme.class}, theme);
                    ((javafx.scene.control.ComboBox<String>) root.lookup("#viewModeCombo")).setValue(mode);
                    window[0].setWidth(1024); window[0].setHeight(768); root.applyCss(); root.layout();
                    assertInside(root.lookup("#viewModeCombo"), root, "View selector must remain reachable");
                    assertEquals("History", ((javafx.scene.text.Text) root.lookup(".workspace-history-menu").lookup(".label").lookup(".text")).getText(),
                            "The History label must not be truncated in compact layouts");
                    capture(root, Path.of("target", "ui-preview", theme.name().toLowerCase() + "-calendar-" + mode.toLowerCase() + "-1024.png"));
                    return null;
                });
            }
            fx(() -> {
                ((javafx.scene.control.ComboBox<String>) root.lookup("#viewModeCombo")).setValue("Day");
                Platform.runLater(() -> calendar.createEvent(today, 9 * 60, 60)); return null;
            });
            fx(() -> {
                DialogPane editor = dialogTitled("New Task"); ((TextField) editor.lookup("#eventTitleField")).setText("Draft survives validation");
                ((TextField) editor.lookup("#eventStartTime")).setText("11:00"); ((TextField) editor.lookup("#eventEndTime")).setText("10:00");
                buttonText(editor, "Save").fire();
                assertTrue(editor.getScene().getWindow().isShowing());
                assertFalse(((Label) editor.lookup("#eventValidationError")).getText().isBlank());
                assertFalse(editor.lookup("#eventValidationError").getParent() instanceof javafx.scene.control.ScrollPane);
                editor.applyCss(); editor.layout();
                assertTrue(editor.lookup("#eventValidationError").localToScene(0, 0).getY() < editor.getHeight() - 45,
                        "Validation must stay visible above the dialog actions");
                assertEquals("Draft survives validation", ((TextField) editor.lookup("#eventTitleField")).getText());
                capture(editor, Path.of("target", "ui-preview", "calendar-retained-draft.png"));
                ((TextField) editor.lookup("#eventStartTime")).setText("09:00"); buttonText(editor, "Save").fire(); return null;
            });
            fx(() -> {
                assertTrue(calendar.tasksSnapshot().get(today).stream().anyMatch(task -> task.getTitle().equals("Draft survives validation")));
                invoke(main[0], "handleUndo", new Class<?>[0]);
                assertTrue(calendar.tasksSnapshot().get(today).stream().noneMatch(task -> task.getTitle().equals("Draft survives validation")));
                invoke(main[0], "handleRedo", new Class<?>[0]);
                Task linked = calendar.tasksSnapshot().get(today).stream().filter(task -> task.getTitle().equals("Northstar proposal review")).findFirst().orElseThrow();
                calendar.deleteTask(today, linked);
                var trash = (com.crm.model.CrmTrash) field(main[0], "trash");
                assertTrue(trash.tasks().get(today).stream().anyMatch(task -> task.getId().equals(linked.getId())));
                Platform.runLater(() -> { try { invoke(main[0], "handleTrash", new Class<?>[0]); } catch (Exception failure) { throw new RuntimeException(failure); } });
                return null;
            });
            fx(() -> {
                DialogPane deleted = dialogTitled("Recently deleted"); buttonText(deleted, "Restore").fire();
                ((Button) deleted.lookupButton(javafx.scene.control.ButtonType.CLOSE)).fire();
                var notes = (NotesController) field(main[0], "notesController");
                Task restored = calendar.tasksSnapshot().get(today).stream().filter(task -> task.getTitle().equals("Northstar proposal review")).findFirst().orElseThrow();
                assertTrue(notes.notesForTask(restored.getId()).stream().anyMatch(note -> note.getTitle().equals("Northstar discovery notes")));
                Platform.runLater(() -> { try { ((ContactsController) field(main[0], "contactsController")).addContact(); } catch (Exception failure) { throw new RuntimeException(failure); } });
                return null;
            });
            fx(() -> {
                DialogPane contact = dialogTitled("Add New Contact"); buttonText(contact, "Save").fire();
                assertTrue(contact.getScene().getWindow().isShowing()); assertFalse(((Label) contact.lookup("#contactValidationError")).getText().isBlank());
                ((Button) contact.lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire(); return null;
            });
            fx(() -> {
                Task series = Task.scheduled("flow-series", "Recurring review", "", 720, 60, "Blue", false); series.setFrequency(Task.Frequency.DAILY); series.setRepeatCount(6);
                Class<?>[] signature = {LocalDate.class, Task.class, CalendarTaskEditor.Result.class, String.class};
                invoke(calendar, "commitEdit", signature, today, null, new CalendarTaskEditor.Result(today, series, java.util.Set.of(), false), null);
                invoke(calendar, "commitEdit", signature, today.plusDays(1), series, new CalendarTaskEditor.Result(today.plusDays(1), series, java.util.Set.of(), true), "This occurrence");
                assertTrue(series.getExcludedDates().contains(today.plusDays(1)));
                assertTrue(((com.crm.model.CrmTrash) field(main[0], "trash")).tasks().get(today.plusDays(1)).stream().anyMatch(task -> task.getTitle().equals("Recurring review")));
                Task changed = Task.scheduled(series.getId(), "Next phase", "", 720, 60, "Blue", false);
                changed.applyMetadata(series.metadata()); changed.setRepeatCount(0); changed.setRepeatUntil(today.plusDays(10));
                invoke(calendar, "commitEdit", signature, today.plusDays(3), series, new CalendarTaskEditor.Result(today.plusDays(3), changed, java.util.Set.of(), false), "This and following");
                Task next = calendar.tasksSnapshot().get(today.plusDays(3)).stream().filter(task -> task.getTitle().equals("Next phase")).findFirst().orElseThrow();
                assertEquals(today.plusDays(10), next.getRepeatUntil()); assertEquals(0, next.getRepeatCount());
                assertEquals(today.plusDays(2), series.getRepeatUntil()); assertNotEquals(series.getId(), next.getId());
                var notes = (NotesController) field(main[0], "notesController"); notes.openById(demo.notes().getFirst().getId());
                var focus = (javafx.scene.control.ToggleButton) root.lookup("#noteFocusButton"); focus.fire();
                assertFalse(root.lookup("#leftSidebarWrapper").isManaged()); assertFalse(root.lookup("#rightSidebarWrapper").isManaged());
                capture(root, Path.of("target", "ui-preview", "notes-focus.png")); focus.fire();
                return null;
            });
            fx(() -> {
                var tasksController = (TasksController) field(main[0], "tasksController");
                List<Task> large = java.util.stream.IntStream.range(0, 10000).mapToObj(i -> {
                    Task task = new Task("Backlog " + i, "", 0, 60, "Blue"); task.setScheduled(false); return task;
                }).toList();
                long start = System.nanoTime(); tasksController.refresh(Map.of(today, large));
                assertTrue((System.nanoTime() - start) / 1_000_000 < 4000, "10,000 tasks should not cause a long UI stall");
                assertEquals(100, root.lookup("#taskListContainer").lookupAll(".task-list-row").size(), "Only one page of task nodes is created");
                var actual = calendar.tasksSnapshot(); tasksController.refresh(actual);
                large.forEach(task -> task.setScheduled(true));
                ((Button) root.lookup("#sideCalendarBtn")).fire();
                calendar.applyState(Map.of(today, large), today, "Day", 1); root.applyCss(); root.layout();
                Button dense = (Button) root.lookup(".calendar-dense-link");
                assertNotNull(dense); assertFalse(dense.isMouseTransparent(), "Dense-day Agenda action must accept clicks");
                assertTrue(root.lookupAll(".activity-item").size() <= 50, "The mini-agenda must not build thousands of nodes");
                dense.fire(); assertEquals("Agenda", calendar.viewMode());
                calendar.applyState(actual, today, "Day", 1);
                // Explicitly capture the restored fixture before the final-save assertion.
                invoke(main[0], "saveCurrentData", new Class<?>[0]);
                return null;
            });
            fx(() -> { main[0].requestClose(window[0]::hide); return null; });
            awaitFx(() -> !window[0].isShowing());
            assertTrue(repository.loadForUser(user.getId()).tasksByDate().get(today).stream().anyMatch(task -> task.getTitle().equals("Draft survives validation")));
            assertEquals("10", repository.loadForUser(user.getId()).preferences().get("calendar.snap"));
        } finally {
            fx(() -> { for (Window current : List.copyOf(Window.getWindows())) current.hide(); return null; });
            System.setProperty("user.home", originalHome);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, arguments);
    }
    private static Button buttonText(DialogPane dialog, String text) {
        return dialog.getButtonTypes().stream().filter(button -> button.getText().equals(text)).map(button -> (Button) dialog.lookupButton(button)).findFirst()
                .orElseGet(() -> dialog.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button -> button.getText().equals(text)).findFirst().orElseThrow());
    }
    private static void awaitFx(Callable<Boolean> condition) throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) { if (fx(condition)) return; Thread.sleep(40); }
        fail("Timed out waiting for the asynchronous UI operation");
    }

    @Test void rendersAllThemesAndExercisesSearchRemindersAndNavigation() throws Exception {
        String originalHome = System.getProperty("user.home");
        // JavaFX loads native font libraries lazily; Windows keeps their files locked until JVM exit.
        Path isolatedHome = Files.createDirectories(Path.of("target", "ui-preview", "user-home").toAbsolutePath());
        System.setProperty("user.home", isolatedHome.toString());
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml"));
            Parent root = fx(() -> {
                Parent loaded = loader.load();
                new Scene(loaded, 1440, 900);
                MainController controller = loader.getController();
                Method apply = MainController.class.getDeclaredMethod("applyUserData", CrmDataSnapshot.class);
                apply.setAccessible(true);
                apply.invoke(controller, demoWorkspace());
                ((Label) loaded.lookup("#currentUserLabel")).setText("Alex Morgan");
                ((Label) loaded.lookup("#saveStatusLabel")).setText("All changes saved");
                return loaded;
            });
            Path previews = Path.of("target", "ui-preview");
            Files.createDirectories(previews);

            for (ThemeService.Theme theme : ThemeService.Theme.values()) {
                fx(() -> {
                    CssParser.errorsProperty().clear();
                    Method apply = MainController.class.getDeclaredMethod("applyTheme", ThemeService.Theme.class);
                    apply.setAccessible(true);
                    apply.invoke(loader.getController(), theme);
                    for (String section : List.of("Home", "Tasks", "Contacts", "Calendar", "Notes", "Dashboard", "Settings")) {
                        ((Button) root.lookup("#side" + section + "Btn")).fire();
                        root.applyCss();
                        root.layout();
                        if (section.equals("Calendar")) {
                            var calendar = (javafx.scene.control.ScrollPane) root.lookup("#calendarScrollPane");
                            double scrollRange = calendar.getContent().getBoundsInLocal().getHeight() - calendar.getViewportBounds().getHeight();
                            calendar.setVvalue(Math.min(1, 480 / Math.max(1, scrollRange)));
                            root.layout();
                        }
                        assertEquals(section, ((Label) root.lookup("#topbarTitleLabel")).getText());
                        capture(root, previews.resolve(theme.name().toLowerCase() + "-" + section.toLowerCase() + ".png"));
                    }
                    assertTrue(CssParser.errorsProperty().isEmpty(), "CSS errors: " + CssParser.errorsProperty());
                    return null;
                });
            }

            fx(() -> {
                ((Button) root.lookup("#sideHomeBtn")).fire();
                ((Button) root.lookup("#remindersButton")).fire();
                assertTrue(root.lookup("#tasksView").isVisible());
                assertEquals("Overdue", ((javafx.scene.control.ComboBox<?>) root.lookup("#taskFilterCombo")).getValue());
                assertTrue(((Label) root.lookup("#reminderCountLabel")).isVisible());
                assertTrue(root.getScene().getStylesheets().getLast().endsWith("typography.css"));
                ((Button) root.lookup("#workspaceSearchButton")).fire();
                return null;
            });

            fx(() -> {
                DialogPane dialog = searchDialog();
                TextField query = (TextField) dialog.lookup("#workspaceSearchInput");
                ListView<?> results = (ListView<?>) dialog.lookup("#workspaceSearchResults");
                query.setText("northstar");
                assertEquals(3, results.getItems().size(), "Contact, task, and note should all be found");
                dialog.applyCss();
                dialog.layout();
                capture(dialog, previews.resolve("workspace-search.png"));
                assertTrue(results.lookupAll(".scroll-bar").stream()
                        .filter(javafx.scene.control.ScrollBar.class::isInstance)
                        .map(javafx.scene.control.ScrollBar.class::cast)
                        .noneMatch(bar -> bar.isVisible() && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL),
                        "Long note excerpts must not force horizontal scrolling");
                query.setText("unmatched text 937");
                assertTrue(results.getItems().isEmpty());
                query.setText("Go to Tasks");
                assertEquals(1, results.getItems().size());
                Event.fireEvent(query, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                return null;
            });

            fx(() -> {
                assertTrue(root.lookup("#tasksView").isVisible());
                assertTrue(Window.getWindows().stream().noneMatch(window -> window.getScene().lookup("#workspaceSearchInput") != null));
                openSearch(root, "Northstar discovery notes");
                activateSearch();
                return null;
            });

            fx(() -> {
                assertTrue(root.lookup("#notesView").isVisible());
                assertTrue(root.lookup("#noteEditorPane").isVisible());
                assertEquals("Northstar discovery notes", ((TextField) root.lookup("#noteTitleField")).getText());
                openSearch(root, "Elena Rossi");
                activateSearch();
                return null;
            });

            fx(() -> {
                DialogPane detail = dialogTitled("Contact details");
                assertTrue(detail.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).anyMatch(label -> label.getText().equals("Elena Rossi")));
                ((Button) detail.lookupButton(javafx.scene.control.ButtonType.CLOSE)).fire();
                assertTrue(root.lookup("#contactsView").isVisible());
                openSearch(root, "Northstar proposal review");
                activateSearch();
                return null;
            });

            fx(() -> {
                closeEditorDialog("Edit Task", "Northstar proposal review");
                openSearch(root, "New task");
                activateSearch();
                return null;
            });

            fx(() -> {
                closeEditorDialog("New Task", null);
                ((Button) root.lookup("#helpButton")).fire();
                DialogPane help = dialogTitled("Make yourself at home");
                ((Button) help.lookupButton(javafx.scene.control.ButtonType.CLOSE)).fire();
                openSearch(root, "");
                Event.fireEvent(searchDialog().lookup("#workspaceSearchInput"), new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                return null;
            });

            fx(() -> {
                assertTrue(Window.getWindows().isEmpty(), "Escape should close search without running an action");
                ((Button) root.lookup("#remindersButton")).fire();
                int before = Integer.parseInt(((Label) root.lookup("#reminderCountLabel")).getText());
                root.applyCss();
                root.layout();
                Node firstTask = ((javafx.scene.layout.VBox) root.lookup("#taskListContainer")).getChildren().stream()
                        .filter(node -> node.getStyleClass().contains("task-list-row")).findFirst().orElseThrow();
                ((javafx.scene.control.CheckBox) firstTask.lookup(".check-box")).fire();
                assertEquals(before - 1, Integer.parseInt(((Label) root.lookup("#reminderCountLabel")).getText()),
                        "Completing an overdue task must immediately update reminders");
                ((Button) root.lookup("#sideHomeBtn")).fire();
                root.resize(1024, 768);
                root.applyCss();
                root.layout();
                capture(root, previews.resolve("home-1024.png"));
                assertFalse(root.lookup("#rightSidebarWrapper").isManaged(), "Agenda should collapse before content becomes cramped");
                assertInside(root.lookup("#workspaceSearchButton"), root, "Search must remain inside the window");
                assertInside(root.lookup("#accountMenuButton"), root, "Account menu must remain inside the window");
                ((Button) root.lookup("#sideTasksBtn")).fire();
                root.resize(800, 700);
                root.applyCss();
                root.layout();
                capture(root, previews.resolve("tasks-800.png"));
                assertFalse(root.lookup("#leftSidebarWrapper").isManaged());
                assertInside(root.lookup("#accountMenuButton"), root, "Account menu must fit at 800 px");
                assertEquals("History", ((javafx.scene.text.Text) root.lookup(".workspace-history-menu").lookup(".label").lookup(".text")).getText());
                root.resize(1440, 900);
                root.layout();
                assertTrue(root.lookup("#rightSidebarWrapper").isManaged(), "Automatically hidden panels should return");
                Parent compactRoot = new FXMLLoader(getClass().getResource("/com/crm/view/MainView.fxml")).load();
                new Scene(compactRoot, 1024, 768);
                compactRoot.applyCss();
                compactRoot.layout();
                assertFalse(compactRoot.lookup("#rightSidebarWrapper").isManaged(), "Compact startup should also collapse the agenda");
                return null;
            });
        } finally {
            fx(() -> {
                List.copyOf(Window.getWindows()).forEach(Window::hide);
                return null;
            });
            System.setProperty("user.home", originalHome);
        }
    }

    private static DialogPane searchDialog() {
        return Window.getWindows().stream().map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup("#workspaceSearchInput") != null).findFirst().orElseThrow();
    }

    private static void openSearch(Parent root, String query) {
        ((Button) root.lookup("#workspaceSearchButton")).fire();
        ((TextField) searchDialog().lookup("#workspaceSearchInput")).setText(query);
    }

    private static void activateSearch() {
        Event.fireEvent(searchDialog().lookup("#workspaceSearchInput"), new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
    }

    private static DialogPane dialogTitled(String title) {
        return Window.getWindows().stream()
                .filter(window -> window instanceof javafx.stage.Stage stage && title.equals(stage.getTitle()))
                .map(window -> (DialogPane) window.getScene().getRoot()).findFirst().orElseThrow();
    }

    private static void closeEditorDialog(String title, String expectedText) {
        assertEquals(1, Window.getWindows().stream()
                .filter(window -> window instanceof javafx.stage.Stage stage && title.equals(stage.getTitle())).count(),
                "Activating a result must open exactly one editor");
        DialogPane dialog = dialogTitled(title);
        try {
            if (expectedText != null) assertTrue(dialog.lookupAll(".text-field").stream()
                    .filter(TextField.class::isInstance).map(TextField.class::cast)
                    .anyMatch(field -> expectedText.equals(field.getText())), "The editor should contain the selected record");
        } finally {
            ((Button) dialog.lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire();
        }
    }

    private static void capture(Node node, Path path) throws Exception {
        ImageIO.write(SwingFXUtils.fromFXImage(node.snapshot(null, null), null), "png", path.toFile());
    }

    private static void assertInside(Node node, Parent root, String message) {
        var bounds = node.localToScene(node.getBoundsInLocal());
        assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= ((Region) root).getWidth() + 1, message + ": " + bounds);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(45, TimeUnit.SECONDS);
    }

    private static CrmDataSnapshot demoWorkspace() {
        LocalDate today = LocalDate.now();
        List<Contact> contacts = List.of(
                new Contact("Elena Rossi", "Northstar Studio", "Design director", "elena@example.test", "+39 02 5550 1000", "Yesterday", "Client", "Website and brand refresh"),
                new Contact("Daniel Chen", "Apex Dynamics", "Product lead", "daniel@example.test", "", "2 days ago", "Follow-up", "Discuss the pilot launch"),
                new Contact("Maya Patel", "Helios Partners", "Founder", "maya@example.test", "", "This week", "Client", "Partnership discovery"),
                new Contact("Luca Ferri", "Meridian Group", "Engineering", "luca@example.test", "", "Last week", "Tech", "Technical review"));
        Task review = new Task("Northstar proposal review", "Align on the new brand direction and next steps.", 10 * 60, 60, "Blue");
        Task completed = new Task("Weekly planning", "Set the priorities for the week.", 9 * 60, 30, "Green");
        completed.setCompleted(true);
        Map<LocalDate, List<Task>> tasks = new java.util.HashMap<>(Map.of(
                today.minusDays(1), List.of(new Task("Send the pilot brief", "Share the scope and delivery dates with Daniel.", 14 * 60, 45, "Orange")),
                today, List.of(completed, review, new Task("Product team catch-up", "Review progress and unblock the team.", 16 * 60, 45, "Purple")),
                today.plusDays(1), List.of(new Task("Partnership discovery", "Explore opportunities with Helios Partners.", 11 * 60, 60, "Green")),
                today.plusDays(3), List.of(new Task("Technical follow-up", "Confirm the integration plan with Luca.", 14 * 60, 30, "Blue"))));
        var todayTasks = new java.util.ArrayList<>(tasks.get(today));
        todayTasks.add(new Task("Design handoff", "Review assets and accessibility notes.", 10 * 60 + 15, 60, "Purple"));
        Task allDay = Task.scheduled("launch-window", "Launch window", "Final checks across the team", 0, 2 * 1440, "Green", false); allDay.setAllDay(true); todayTasks.add(allDay);
        tasks.put(today, todayTasks);
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        for (int day = 0; day < 5; day++) {
            LocalDate date = monday.plusDays(day); var entries = new java.util.ArrayList<>(tasks.getOrDefault(date, List.of()));
            entries.add(new Task(List.of("Weekly direction", "Client discovery", "Project review", "Planning session", "Delivery check-in").get(day), "Clear outcomes and next steps.", (9 + day % 3) * 60, 60, List.of("Blue", "Green", "Purple", "Orange", "Blue").get(day)));
            tasks.put(date, entries);
        }
        review.setContactId(contacts.getFirst().getId()); review.setPriority(Task.Priority.HIGH);
        Note note = new Note("Northstar discovery notes", NoteFormat.MARKDOWN);
        note.linkTask(review.getId());
        note.setContactId(contacts.getFirst().getId());
        note.setContent("# A clearer direction\n\nA considered visual identity and an easier daily workflow.\n\n- Review the proposal\n- Share the next steps");
        Note second = new Note("Ideas for next quarter", NoteFormat.MARKDOWN);
        second.setContent("A place to collect opportunities, conversations, and promising directions.");
        return new CrmDataSnapshot(contacts, tasks, List.of(note, second), today, "Week", 1.0);
    }
}
