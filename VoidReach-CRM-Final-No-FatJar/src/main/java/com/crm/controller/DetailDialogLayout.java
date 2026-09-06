package com.crm.controller;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

/** Shared presentation for the task editor and contact profile. */
final class DetailDialogLayout {
    private DetailDialogLayout() { }

    static void configure(Dialog<?> dialog, double width) {
        dialog.setResizable(true);
        dialog.getDialogPane().getStyleClass().add("detail-dialog");
        dialog.getDialogPane().setPrefWidth(width);
        dialog.getDialogPane().setMinWidth(480);
    }

    static HBox header(String icon, String title, String subtitle) {
        StackPane mark = new StackPane(icon(icon));
        mark.getStyleClass().add("detail-dialog-mark");
        VBox copy = new VBox(5, label(title, "detail-dialog-title"), label(subtitle, "detail-dialog-subtitle"));
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox header = new HBox(14, mark, copy);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("detail-dialog-header");
        return header;
    }

    static VBox section(String title, Node... children) {
        VBox section = new VBox(12, label(title, "detail-section-title"));
        section.getChildren().addAll(children);
        section.getStyleClass().add("detail-section");
        return section;
    }

    static VBox field(String title, Node input) {
        Label caption = label(title, "field-caption");
        caption.setLabelFor(input);
        if (input instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        VBox field = new VBox(6, caption, input);
        field.setMinWidth(0);
        return field;
    }

    static HBox columns(Region... fields) {
        HBox row = new HBox(12, fields);
        for (Region field : fields) {
            field.setPrefWidth(0);
            field.setMinWidth(0);
            HBox.setHgrow(field, Priority.ALWAYS);
        }
        return row;
    }

    static ScrollPane scroller(Node content, double height) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(height);
        scroll.setMinHeight(160);
        scroll.getStyleClass().add("detail-scroll");
        return scroll;
    }

    static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinWidth(0);
        return label;
    }

    static FontIcon icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("detail-icon");
        icon.setIconSize(16);
        return icon;
    }
}
