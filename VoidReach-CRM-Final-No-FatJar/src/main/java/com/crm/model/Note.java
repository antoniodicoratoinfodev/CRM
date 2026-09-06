package com.crm.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Note {
    public static final String DEFAULT_FONT_FAMILY = "System";
    public static final double DEFAULT_FONT_SIZE = 16.0;
    public static final int DEFAULT_FONT_WEIGHT = 400;
    public static final String DEFAULT_PREVIEW_FONT_FAMILY = "System";
    public static final double DEFAULT_PREVIEW_FONT_SIZE = 18.0;
    private final String id;
    private final NoteFormat format;
    private String title;
    private String content;
    private final LinkedHashSet<String> linkedTaskIds = new LinkedHashSet<>();
    private String fontFamily;
    private double fontSize;
    private int fontWeight;
    private boolean italic;
    private String previewFontFamily;
    private double previewFontSize;
    private String previewTextColor;
    private String folderId;
    private String contactId = "";

    public Note(String title, NoteFormat format) {
        this(UUID.randomUUID().toString(), title, "", format, "");
    }

    public Note(String id, String title, String content, NoteFormat format, String linkedTaskId) {
        this(id, title, content, format, linkedTaskId,
                DEFAULT_FONT_FAMILY, DEFAULT_FONT_SIZE, DEFAULT_FONT_WEIGHT, false);
    }

    public Note(String id, String title, String content, NoteFormat format, String linkedTaskId,
                String fontFamily, double fontSize, int fontWeight, boolean italic) {
        this(id, title, content, format, linkedTaskId, fontFamily, fontSize, fontWeight, italic,
                DEFAULT_PREVIEW_FONT_FAMILY, DEFAULT_PREVIEW_FONT_SIZE, "");
    }

    public Note(String id, String title, String content, NoteFormat format, String linkedTaskId,
                String fontFamily, double fontSize, int fontWeight, boolean italic,
                String previewFontFamily, double previewFontSize, String previewTextColor) {
        this(id, title, content, format, linkedTaskId, fontFamily, fontSize, fontWeight, italic,
                previewFontFamily, previewFontSize, previewTextColor, "");
    }

    public Note(String id, String title, String content, NoteFormat format, String linkedTaskId,
                String fontFamily, double fontSize, int fontWeight, boolean italic,
                String previewFontFamily, double previewFontSize, String previewTextColor, String folderId) {
        this(id, title, content, format, linkedTaskId == null || linkedTaskId.isBlank()
                        ? List.of() : List.of(linkedTaskId),
                fontFamily, fontSize, fontWeight, italic,
                previewFontFamily, previewFontSize, previewTextColor, folderId);
    }

    public Note(String id, String title, String content, NoteFormat format, Collection<String> linkedTaskIds,
                String fontFamily, double fontSize, int fontWeight, boolean italic,
                String previewFontFamily, double previewFontSize, String previewTextColor, String folderId) {
        this.id = requireId(id);
        this.title = safe(title);
        this.content = safe(content);
        this.format = Objects.requireNonNull(format);
        setLinkedTaskIds(linkedTaskIds);
        setFontFamily(fontFamily);
        setFontSize(fontSize);
        setFontWeight(fontWeight);
        this.italic = italic;
        setPreviewFontFamily(previewFontFamily);
        setPreviewFontSize(previewFontSize);
        setPreviewTextColor(previewTextColor);
        this.folderId = safe(folderId);
    }

    public String getId() { return id; }
    public String getContactId() { return contactId; }
    public void setContactId(String value) { contactId = safe(value); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = safe(title); }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = safe(content); }
    public NoteFormat getFormat() { return format; }
    /** Compatibility accessor for older callers that expected at most one linked task. */
    public String getLinkedTaskId() { return linkedTaskIds.stream().findFirst().orElse(""); }
    /** Compatibility mutator: replaces every link with the supplied task, or clears all links. */
    public void setLinkedTaskId(String linkedTaskId) {
        setLinkedTaskIds(linkedTaskId == null || linkedTaskId.isBlank() ? List.of() : List.of(linkedTaskId));
    }
    public List<String> getLinkedTaskIds() { return List.copyOf(linkedTaskIds); }
    public void setLinkedTaskIds(Collection<String> taskIds) {
        linkedTaskIds.clear();
        if (taskIds == null) return;
        taskIds.stream().map(Note::safe).map(String::trim).filter(value -> !value.isEmpty())
                .forEach(linkedTaskIds::add);
    }
    public boolean isLinkedToTask(String taskId) { return taskId != null && linkedTaskIds.contains(taskId); }
    public void linkTask(String taskId) {
        String value = safe(taskId).trim();
        if (!value.isEmpty()) linkedTaskIds.add(value);
    }
    public void unlinkTask(String taskId) { linkedTaskIds.remove(safe(taskId).trim()); }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily == null || fontFamily.isBlank() ? DEFAULT_FONT_FAMILY : fontFamily.trim();
    }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) {
        if (!Double.isFinite(fontSize) || fontSize < 10 || fontSize > 48) {
            throw new IllegalArgumentException("Note font size must be between 10 and 48.");
        }
        this.fontSize = fontSize;
    }
    public int getFontWeight() { return fontWeight; }
    public void setFontWeight(int fontWeight) {
        if (fontWeight < 100 || fontWeight > 900 || fontWeight % 100 != 0) {
            throw new IllegalArgumentException("Note font weight must be between 100 and 900.");
        }
        this.fontWeight = fontWeight;
    }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
    public String getPreviewFontFamily() { return previewFontFamily; }
    public void setPreviewFontFamily(String previewFontFamily) {
        this.previewFontFamily = previewFontFamily == null || previewFontFamily.isBlank()
                ? DEFAULT_PREVIEW_FONT_FAMILY : previewFontFamily.trim();
    }
    public double getPreviewFontSize() { return previewFontSize; }
    public void setPreviewFontSize(double previewFontSize) {
        if (!Double.isFinite(previewFontSize) || previewFontSize < 10 || previewFontSize > 48) {
            throw new IllegalArgumentException("Preview font size must be between 10 and 48.");
        }
        this.previewFontSize = previewFontSize;
    }
    public String getPreviewTextColor() { return previewTextColor; }
    public void setPreviewTextColor(String previewTextColor) {
        String value = safe(previewTextColor).trim();
        if (!value.isEmpty() && !value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Preview color must use #RRGGBB format.");
        }
        this.previewTextColor = value.toUpperCase();
    }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = safe(folderId); }

    private static String requireId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A note must have an ID.");
        return value;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
