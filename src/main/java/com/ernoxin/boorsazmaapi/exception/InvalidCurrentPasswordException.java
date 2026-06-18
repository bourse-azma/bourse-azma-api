package com.ernoxin.boorsazmaapi.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("رمز عبور فعلی اشتباه است.");
    }
}
