package com.finguard.transaction.exception;

/** A Kafka event that can never be processed; sent straight to the DLT without retries. */
public class MalformedEventException extends RuntimeException {
    public MalformedEventException(String message) {
        super(message);
    }
}
