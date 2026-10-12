package com.finguard.transaction.exception;

public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException() {
        super("Idempotency-Key was already used for a different request");
    }
}
