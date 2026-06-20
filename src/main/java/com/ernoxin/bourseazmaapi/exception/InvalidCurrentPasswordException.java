package com.ernoxin.bourseazmaapi.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("رمز عبور فعلی اشتباه است.");
    }
}
