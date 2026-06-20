package com.ernoxin.bourseazmaapi.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String userMessage) {
        super(userMessage);
    }
}
