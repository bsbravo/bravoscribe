package com.bravoscribe.userservice.exception;

public sealed interface UserError permits
        UserError.EmailAlreadyExists,
        UserError.InvalidCredentials,
        UserError.UserNotFound,
        UserError.UserDeactivated,
        UserError.InvalidRefreshToken,
        UserError.InvalidPasswordResetToken,
        UserError.PasswordResetTokenExpired,
        UserError.PasswordResetTokenAlreadyUsed,
        UserError.WrongCurrentPassword,
        UserError.PageSizeExceeded {

    record EmailAlreadyExists(String email) implements UserError {}
    record InvalidCredentials() implements UserError {}
    record UserNotFound(String userId) implements UserError {}
    record UserDeactivated() implements UserError {}
    record InvalidRefreshToken() implements UserError {}
    record InvalidPasswordResetToken() implements UserError {}
    record PasswordResetTokenExpired() implements UserError {}
    record PasswordResetTokenAlreadyUsed() implements UserError {}
    record WrongCurrentPassword() implements UserError {}
    record PageSizeExceeded(int max) implements UserError {}
}
