package com.finguard.fraud.exception;

/** A request header has an invalid value; mapped to 400 Bad Request. */
public class InvalidHeaderException extends RuntimeException {
    public InvalidHeaderException(String message) {
        super(message);
    }
}
