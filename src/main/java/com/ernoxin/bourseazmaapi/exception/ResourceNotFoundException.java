package com.ernoxin.bourseazmaapi.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String userMessage) {
        super(userMessage);
    }
}
