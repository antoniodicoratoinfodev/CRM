package com.crm.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ContactInteraction(String id, LocalDate date, String kind, String summary) {
    public ContactInteraction {
        Objects.requireNonNull(id); Objects.requireNonNull(date); Objects.requireNonNull(kind); Objects.requireNonNull(summary);
        if (summary.isBlank()) throw new IllegalArgumentException("Describe the interaction.");
    }
    public ContactInteraction(LocalDate date, String kind, String summary) { this(UUID.randomUUID().toString(), date, kind, summary); }
}
