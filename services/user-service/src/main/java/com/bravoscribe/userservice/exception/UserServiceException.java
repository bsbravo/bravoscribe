package com.bravoscribe.userservice.exception;

public class UserServiceException extends RuntimeException {

    private final UserError error;

    public UserServiceException(UserError error) {
        super(error.getClass().getSimpleName());
        this.error = error;
    }

    public UserError getError() {
        return error;
    }
}
