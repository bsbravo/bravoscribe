package com.bravoscribe.journalservice.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JournalServiceException.class)
    public ProblemDetail handleJournalServiceException(JournalServiceException ex) {
        return switch (ex.getError()) {
            case JournalError.EntryNotFound e -> problem(
                    HttpStatus.NOT_FOUND, "not-found", "Entry not found");

            case JournalError.DuplicateEntryDate e -> problem(
                    HttpStatus.CONFLICT, "duplicate-entry-date",
                    "An entry for " + e.date() + " already exists");

            case JournalError.TagNotFound e -> problem(
                    HttpStatus.NOT_FOUND, "not-found", "Tag not found");

            case JournalError.DuplicateTagName e -> problem(
                    HttpStatus.CONFLICT, "duplicate-tag-name",
                    "A tag named '" + e.name() + "' already exists");

            case JournalError.ExportRangeExceeded e -> problem(
                    HttpStatus.BAD_REQUEST, "export-range-exceeded",
                    "Export range of " + e.days() + " days exceeds the 366-day limit");

            case JournalError.ExportNoEntries e -> problem(
                    HttpStatus.NOT_FOUND, "not-found",
                    "No entries found in the specified date range");
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:bravoscribe:error:validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setDetail(detail);
        return pd;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:bravoscribe:error:validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:bravoscribe:error:validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    private ProblemDetail problem(HttpStatus status, String errorCode, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create("urn:bravoscribe:error:" + errorCode));
        pd.setTitle(toTitle(errorCode));
        pd.setDetail(detail);
        return pd;
    }

    private String toTitle(String code) {
        String[] parts = code.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
