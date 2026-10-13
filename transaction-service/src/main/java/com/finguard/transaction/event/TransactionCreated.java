package com.finguard.transaction.event;

import com.finguard.transaction.entity.Transaction;

/**
 * Domain event raised by {@link com.finguard.transaction.service.TransactionService}.
 * The service does not know how (or whether) the event leaves the service.
 */
public record TransactionCreated(Transaction transaction, String correlationId) {}
