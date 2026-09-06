package com.crm.repository;

import com.crm.model.CrmDataSnapshot;

/**
 * Result of reading a portable file without committing it: the parsed workspace plus the account
 * that produced the file ({@code owner} is null for legacy files written before ownership stamping).
 */
public record ImportedWorkspace(ExportOwner owner, CrmDataSnapshot snapshot, java.util.List<String> warnings) {
    public ImportedWorkspace { warnings = java.util.List.copyOf(warnings); }
    public ImportedWorkspace(ExportOwner owner, CrmDataSnapshot snapshot) { this(owner, snapshot, java.util.List.of()); }
}
