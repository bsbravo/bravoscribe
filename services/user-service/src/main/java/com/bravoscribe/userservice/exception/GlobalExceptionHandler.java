package com.bravoscribe.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserServiceException.class)
    public ProblemDetail handleUserServiceException(UserServiceException ex) {
        return switch (ex.getError()) {
            case UserError.EmailAlreadyExists e -> problem(
                    HttpStatus.CONFLICT, "email-already-exists",
                    "Email address is already registered");

            case UserError.InvalidCredentials e -> problem(
                    HttpStatus.UNAUTHORIZED, "invalid-credentials",
                    "Invalid email or password");

            case UserError.UserNotFound e -> problem(
                    HttpStatus.NOT_FOUND, "user-not-found",
                    "User not found");

            case UserError.UserDeactivated e -> problem(
                    HttpStatus.UNAUTHORIZED, "account-deactivated",
                    "Account is deactivated");

            case UserError.InvalidRefreshToken e -> problem(
                    HttpStatus.UNAUTHORIZED, "invalid-refresh-token",
                    "Refresh token is invalid or expired");

            case UserError.InvalidPasswordResetToken e -> problem(
                    HttpStatus.BAD_REQUEST, "invalid-reset-token",
                    "Password reset token is invalid");

            case UserError.PasswordResetTokenExpired e -> problem(
                    HttpStatus.BAD_REQUEST, "reset-token-expired",
                    "Password reset token has expired");

            case UserError.PasswordResetTokenAlreadyUsed e -> problem(
                    HttpStatus.BAD_REQUEST, "reset-token-used",
                    "Password reset token has already been used");

            case UserError.WrongCurrentPassword e -> problem(
                    HttpStatus.UNPROCESSABLE_ENTITY, "wrong-current-password",
                    "Current password is incorrect");

            case UserError.PageSizeExceeded e -> problem(
                    HttpStatus.BAD_REQUEST, "page-size-exceeded",
                    "Page size cannot exceed " + e.max());
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
