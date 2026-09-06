package com.crm.model;

/** Per-account appearance preference; independent of the packaged executable icon. */
public enum AppIcon {
    BOOK("Book (original)"), V("V (modern)");

    private final String label;
    AppIcon(String label) { this.label = label; }
    @Override public String toString() { return label; }
    public static AppIcon from(String value) {
        try { return valueOf(value); } catch (IllegalArgumentException | NullPointerException invalid) { return V; }
    }
}
