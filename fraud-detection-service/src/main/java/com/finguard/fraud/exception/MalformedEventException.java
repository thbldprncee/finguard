package com.finguard.fraud.exception;

/** A Kafka event that can never be processed; sent straight to the DLT without retries. */
public class MalformedEventException extends IllegalArgumentException {
    public MalformedEventException(String message) {
        super(message);
    }
}
