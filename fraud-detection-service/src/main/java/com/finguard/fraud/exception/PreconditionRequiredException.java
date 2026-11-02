package com.finguard.fraud.exception;

/** A conditional request is mandatory here (If-Match missing); mapped to 428 Precondition Required. */
public class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException(String message) {
        super(message);
    }
}
