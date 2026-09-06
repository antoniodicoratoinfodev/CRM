package com.crm.service;

import javafx.scene.text.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Bundled, platform-independent typefaces for UI, reading and source code. */
public final class Typography {
    public static final String UI = "Inter";
    public static final String CODE = "JetBrains Mono";
    private static boolean loaded;
    private Typography() { }

    public static synchronized void load() {
        if (loaded) return;
        for (String style : List.of("Regular", "Medium", "SemiBold", "Bold", "Italic", "MediumItalic", "SemiBoldItalic", "BoldItalic"))
            loadFont("Inter-" + style + ".ttf");
        for (String style : List.of("Regular", "Medium", "SemiBold", "Bold", "Italic", "MediumItalic", "SemiBoldItalic", "BoldItalic"))
            loadFont("JetBrainsMono-" + style + ".ttf");
        loaded = true;
    }
    private static void loadFont(String file) {
        try (var stream = Objects.requireNonNull(Typography.class.getResourceAsStream("/fonts/" + file), "Missing font: " + file)) {
            if (Font.loadFont(stream, 14) == null) throw new IllegalStateException("Cannot load font: " + file);
        } catch (IOException error) { throw new IllegalStateException("Cannot read font: " + file, error); }
    }
    public static String resolve(String family, boolean code) {
        return family == null || family.isBlank() || family.equals("System") ? (code ? CODE : UI) : family;
    }
    public static String editorFamily(String family, boolean code, int weight) {
        String resolved = resolve(family, code);
        if (UI.equals(resolved) || CODE.equals(resolved)) {
            if (weight >= 500 && weight < 600) return resolved + " Medium";
            if (weight >= 600 && weight < 700) return resolved + " SemiBold";
        }
        return resolved;
    }
    public static String resource(String path) {
        try (var stream = Objects.requireNonNull(Typography.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException("Cannot read " + path, error); }
    }
    private static String webFont(String family, String file, int weight, String style) {
        try (var stream = Objects.requireNonNull(Typography.class.getResourceAsStream("/fonts/" + file), file)) {
            return "@font-face{font-family:'" + family + "';font-style:" + style + ";font-weight:" + weight
                    + ";src:url(data:font/woff2;base64," + Base64.getEncoder().encodeToString(stream.readAllBytes()) + ") format('woff2');}\n";
        } catch (IOException error) { throw new IllegalStateException("Cannot read " + file, error); }
    }
    private static class WebFonts {
        static final String CSS = webFont(UI, "Inter-Regular.woff2", 400, "normal")
                + webFont(UI, "Inter-Italic.woff2", 400, "italic")
                + webFont("Inter SemiBold", "Inter-SemiBold.woff2", 400, "normal")
                + webFont(UI, "Inter-Bold.woff2", 700, "normal")
                + webFont(UI, "Inter-BoldItalic.woff2", 700, "italic")
                + webFont(CODE, "JetBrainsMono-Regular.woff2", 400, "normal");
    }
    public static String webFonts() { return WebFonts.CSS; }
}
