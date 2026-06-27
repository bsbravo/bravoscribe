package com.bravoscribe.journalservice.exception;

public sealed interface JournalError permits
        JournalError.EntryNotFound,
        JournalError.DuplicateEntryDate,
        JournalError.TagNotFound,
        JournalError.DuplicateTagName,
        JournalError.ExportRangeExceeded,
        JournalError.ExportNoEntries {

    record EntryNotFound() implements JournalError {}
    record DuplicateEntryDate(String date) implements JournalError {}
    record TagNotFound() implements JournalError {}
    record DuplicateTagName(String name) implements JournalError {}
    record ExportRangeExceeded(int days) implements JournalError {}
    record ExportNoEntries() implements JournalError {}
}
