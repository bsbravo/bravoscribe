package com.bravoscribe.journalservice.exception;

public class JournalServiceException extends RuntimeException {

    private final JournalError error;

    public JournalServiceException(JournalError error) {
        super(error.getClass().getSimpleName());
        this.error = error;
    }

    public JournalError getError() {
        return error;
    }
}
