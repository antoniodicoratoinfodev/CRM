package com.crm.view;

import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import com.crm.model.AppIcon;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Resolution-independent VoidReach mark: a clear V and a forward point. */
public final class BrandMark extends Region {
    private static final ObjectProperty<AppIcon> selected = new SimpleObjectProperty<>(AppIcon.V);
    private final Group drawing = new Group();
    private final ChangeListener<AppIcon> selectionListener = (observable, before, after) -> draw(after);
    private static class BookAsset {
        private static final Image IMAGE = new Image(BrandMark.class.getResource("/images/app-icon.png").toExternalForm());
    }
    public BrandMark() {
        this(selected.get());
        selected.addListener(new WeakChangeListener<>(selectionListener));
    }
    private BrandMark(AppIcon icon) {
        getChildren().add(drawing);
        setPrefSize(36, 36); setMinSize(0, 0); setMouseTransparent(true);
        draw(icon);
    }
    private void draw(AppIcon icon) {
        setAccessibleText("VoidReach · " + icon);
        if (icon == AppIcon.BOOK) {
            ImageView book = new ImageView(BookAsset.IMAGE); book.setFitWidth(64); book.setFitHeight(64);
            book.setPreserveRatio(true); book.setSmooth(true); drawing.getChildren().setAll(book);
            requestLayout(); return;
        }
        Rectangle tile = new Rectangle(64, 64); tile.setArcWidth(18); tile.setArcHeight(18); tile.setFill(Color.web("#182237"));
        SVGPath v = new SVGPath(); v.setContent("M 12 17 L 22 17 L 32 40 L 42 17 L 52 17 L 37 51 L 27 51 Z");
        v.setFill(Color.web("#dbe4ff"));
        Circle point = new Circle(50, 10, 4); point.setFill(Color.web("#7ca9ff"));
        drawing.getChildren().setAll(tile, v, point); requestLayout();
    }
    @Override protected void layoutChildren() {
        double size = Math.min(getWidth(), getHeight()); drawing.getTransforms().setAll(new Scale(size / 64, size / 64));
        drawing.setLayoutX((getWidth() - size) / 2); drawing.setLayoutY((getHeight() - size) / 2);
    }
    public static WritableImage icon(int size) {
        return icon(AppIcon.V, size);
    }
    public static WritableImage icon(AppIcon icon, int size) {
        BrandMark mark = new BrandMark(icon); mark.resize(size, size); mark.layout();
        SnapshotParameters parameters = new SnapshotParameters(); parameters.setFill(Color.TRANSPARENT);
        return mark.snapshot(parameters, new WritableImage(size, size));
    }
    public static AppIcon selectedIcon() { return selected.get(); }
    public static void select(AppIcon icon) {
        selected.set(icon == null ? AppIcon.V : icon);
        Image small = icon(selected.get(), 32), large = icon(selected.get(), 128);
        for (Window window : java.util.List.copyOf(Window.getWindows())) {
            if (window instanceof Stage stage) stage.getIcons().setAll(small, large);
        }
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            try {
                if (java.awt.Taskbar.isTaskbarSupported() && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE))
                    java.awt.Taskbar.getTaskbar().setIconImage(javafx.embed.swing.SwingFXUtils.fromFXImage(icon(selected.get(), 256), null));
            } catch (RuntimeException ignored) { /* In-app/window selection still applies without Dock support. */ }
        }
    }
}
