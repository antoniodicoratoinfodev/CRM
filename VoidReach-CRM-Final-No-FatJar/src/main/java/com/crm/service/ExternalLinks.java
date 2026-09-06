package com.crm.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** User-activated links open in the OS browser/mail app, never in the reading surface. */
public final class ExternalLinks {
    private static Consumer<String> opener;
    private ExternalLinks() { }
    public static void setOpener(Consumer<String> action) { opener = action; }
    public static boolean allowed(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return Set.of("https", "http").contains(scheme) ? uri.getHost() != null
                    : scheme.equals("mailto") && !uri.getSchemeSpecificPart().isBlank();
        } catch (IllegalArgumentException | NullPointerException invalid) { return false; }
    }
    public static void open(String address) {
        if (!allowed(address)) throw new IllegalArgumentException("This link needs an absolute http, https or mailto address.");
        if (opener == null) throw new IllegalStateException("The system browser is not available.");
        opener.accept(address);
    }
}
