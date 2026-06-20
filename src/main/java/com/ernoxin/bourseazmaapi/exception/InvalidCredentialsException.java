package com.ernoxin.bourseazmaapi.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("نام کاربری یا رمز عبور اشتباه است.");
    }
}
