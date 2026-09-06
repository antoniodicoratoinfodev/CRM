package com.crm.view;

import com.crm.model.Note;
import com.crm.model.NoteFormat;
import com.crm.service.ThemeService;
import com.crm.service.Typography;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Run with the packaged runtime and app/*.jar, not Maven's runtime dependencies. */
public final class PackagedPreviewSmoke {
    public static void main(String[] args) {
        Platform.startup(() -> {
            try {
                Typography.load();
                MarkdownPreviewView view = new MarkdownPreviewView(address -> { });
                Stage stage = new Stage(); stage.setScene(new Scene(view, 960, 760));
                stage.setTitle("VoidReach packaged preview verification");
                new ThemeService(() -> stage).applyTo(stage.getScene()); stage.show();
                WebView web = (WebView) view.lookup("#markdownWebView");
                PauseTransition timeout = new PauseTransition(Duration.seconds(20));
                timeout.setOnFinished(event -> { System.err.println("Packaged preview timed out"); System.exit(2); });
                timeout.play();
                web.getEngine().getLoadWorker().stateProperty().addListener((observable, before, after) -> {
                    if (after != Worker.State.SUCCEEDED) return;
                    PauseTransition paint = new PauseTransition(Duration.millis(300));
                    paint.setOnFinished(event -> {
                        try {
                            if (web.getEngine().isJavaScriptEnabled() || web.getEngine().getDocument().getElementsByTagName("table").getLength() != 1)
                                throw new IllegalStateException("Packaged Markdown did not load correctly");
                            Path output = Path.of(args[0]); Files.createDirectories(output.toAbsolutePath().getParent());
                            ImageIO.write(SwingFXUtils.fromFXImage(view.snapshot(null, null), null), "png", output.toFile());
                            System.out.println("PACKAGED_PREVIEW_OK: fonts, native WebView, Markdown tables and screenshot");
                            timeout.stop(); stage.close(); Platform.exit();
                        } catch (Exception failure) { failure.printStackTrace(); System.exit(1); }
                    });
                    paint.play();
                });
                Note note = new Note("Packaged reading view", NoteFormat.MARKDOWN);
                note.setContent("# A clearer direction\n\nA **verified** reading experience, with bundled fonts.\n\n| Area | Font |\n| --- | --- |\n| Interface | Inter |\n| Code | JetBrains Mono |\n\n```java\nreturn \"Ready\";\n```");
                view.show(note, ThemeService.Theme.DARK);
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        });
    }
}
