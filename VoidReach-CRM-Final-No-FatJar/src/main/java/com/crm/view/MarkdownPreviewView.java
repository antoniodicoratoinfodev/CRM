package com.crm.view;

import com.crm.model.Note;
import com.crm.service.MarkdownPreviewService;
import com.crm.service.ThemeService.Theme;
import com.crm.service.Typography;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Lazy reading surface: selectable text, native scrolling, and explicit link/image actions. */
public final class MarkdownPreviewView extends VBox {
    private final WebView web = new WebView();
    private final Label status = new Label("Reading view");
    private final Button copy = new Button("Copy text");
    private final Consumer<String> openLink;
    private final EventListener clickListener = this::clicked;
    private final MarkdownPreviewService renderer = new MarkdownPreviewService();
    private String sourceKey = "", plainText = "", readingStyle = "";
    private long revision;
    private boolean renderReady;

    public MarkdownPreviewView(Consumer<String> openLink) {
        this.openLink = openLink; setId("markdownReadingView"); getStyleClass().add("markdown-reading-view");
        web.setId("markdownWebView"); web.setMinHeight(0); web.setContextMenuEnabled(false); web.getEngine().setJavaScriptEnabled(false);
        web.setFontSmoothingType(javafx.scene.text.FontSmoothingType.GRAY);
        copy.getStyleClass().add("text-button"); copy.setDisable(true); copy.setOnAction(e -> copy(plainText));
        status.getStyleClass().add("reading-status"); Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(12, status, spacer, copy); toolbar.setAlignment(Pos.CENTER_LEFT); toolbar.getStyleClass().add("reading-toolbar");
        getChildren().setAll(toolbar, web); VBox.setVgrow(web, Priority.ALWAYS);
        web.getEngine().getLoadWorker().stateProperty().addListener((observable, before, after) -> {
            if (after == Worker.State.SUCCEEDED && web.getEngine().getDocument() != null) {
                updateStyle(); ((EventTarget) web.getEngine().getDocument()).addEventListener("click", clickListener, false);
                web.setOpacity(1);
                status.setText(renderReady ? "Reading view" : "Preview unavailable. Your full note remains in the editor.");
                copy.setDisable(!renderReady);
            } else if (after == Worker.State.FAILED) {
                sourceKey = ""; copy.setDisable(true);
                status.setText("Preview could not load. Your note remains available in the editor.");
            }
        });
    }
    public void show(Note note, Theme theme) {
        readingStyle = style(note, theme);
        String key = note.getId() + "\n" + note.getContent();
        if (key.equals(sourceKey)) { updateStyle(); return; }
        sourceKey = key; long requested = ++revision; String source = note.getContent();
        status.setText("Preparing preview…"); copy.setDisable(true); plainText = ""; renderReady = false;
        web.setOpacity(0); web.getEngine().getLoadWorker().cancel();
        CompletableFuture.supplyAsync(() -> renderer.render(source)).whenComplete((rendered, failure) -> Platform.runLater(() -> {
            if (requested != revision) return;
            if (failure != null) {
                sourceKey = "";
                loadBody("<p class=\"empty-note\">Preview unavailable for this note. Its full content remains available in the editor.</p>");
                return;
            }
            plainText = rendered.plainText(); renderReady = true;
            String body = rendered.body().isBlank() ? "<p class=\"empty-note\">This note is empty. Return to the editor to start writing.</p>" : rendered.body();
            loadBody(body);
        }));
    }
    public void focus() { web.requestFocus(); }
    private void loadBody(String body) {
        web.getEngine().loadContent("<!doctype html><html><head><meta charset=\"UTF-8\">"
                    + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; font-src data:; img-src https: http:; script-src 'none';\">"
                    + "<style>" + Typography.webFonts() + Typography.resource("/css/markdown-preview.css") + "</style>"
                    + "<style id=\"reading-style\">" + readingStyle + "</style></head><body><main id=\"article\">" + body + "</main></body></html>");
    }
    private void updateStyle() {
        var document = web.getEngine().getDocument();
        if (document != null && document.getElementById("reading-style") != null) document.getElementById("reading-style").setTextContent(readingStyle);
    }
    private void clicked(Event event) {
        for (Node node = (Node) event.getTarget(); node != null; node = node.getParentNode()) if (node instanceof Element element) {
            if (element.hasAttribute("data-copy")) {
                event.preventDefault(); Element code = web.getEngine().getDocument().getElementById(element.getAttribute("data-copy"));
                if (code != null) { copy(code.getTextContent()); element.setTextContent("Copied"); } return;
            }
            if (element.hasAttribute("data-load-image")) {
                event.preventDefault(); Element holder = web.getEngine().getDocument().getElementById(element.getAttribute("data-load-image"));
                if (holder == null) return;
                Element image = web.getEngine().getDocument().createElement("img");
                image.setAttribute("alt", holder.getAttribute("data-alt")); image.setAttribute("src", holder.getAttribute("data-image-url"));
                holder.replaceChild(image, element); return;
            }
            if (element.getTagName().equalsIgnoreCase("a")) {
                String address = element.getAttribute("href");
                if (address.startsWith("#")) return;
                event.preventDefault();
                if (address.isBlank()) status.setText("This link has no supported absolute address.");
                else openLink.accept(address);
                return;
            }
        }
    }
    private void copy(String value) { ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content); status.setText("Copied to clipboard"); }
    private static String style(Note note, Theme theme) {
        String[] palette = switch (theme) {
            case LIGHT -> new String[]{"#ffffff", "#202939", "#657286", "#e1e5eb", "#f4f6f9", "#4568bd"};
            case DARK -> new String[]{"#202630", "#e7ebf2", "#a0adbf", "#323c49", "#262e39", "#a0b8f4"};
            case BLUE_GRAY -> new String[]{"#232f41", "#e6edf8", "#a7b6cc", "#39485e", "#29364a", "#a7c2f7"};
            case GRAY_BLUE -> new String[]{"#30363e", "#e6ecf2", "#b0bcc9", "#47515d", "#363f49", "#acc8e5"};
        };
        String family = Typography.resolve(note.getPreviewFontFamily(), false).replaceAll("[^\\p{L}\\p{N} ._-]", "");
        String custom = note.getPreviewTextColor(); String ink = custom.matches("#[0-9a-fA-F]{6}") ? custom : palette[1];
        return ":root{--paper:" + palette[0] + ";--ink:" + ink + ";--muted:" + palette[2] + ";--line:" + palette[3]
                + ";--raised:" + palette[4] + ";--link:" + palette[5] + ";--reading-size:" + note.getPreviewFontSize() + "px;--reading-font:'" + family
                + "';--heading-font:'" + (family.equals(Typography.UI) ? "Inter SemiBold" : family)
                + "';--heading-weight:" + (family.equals(Typography.UI) ? "400" : "600") + ";}"
                + (theme == Theme.LIGHT ? ":root{--keyword:#7651a8;--string:#267447;--number:#a35622;--type:#276580;}" : "");
    }
}
