package com.finguard.fraud.exception;

/**
 * The client edited an outdated copy: the version it sent in If-Match is not the current one.
 * Mapped to 412 Precondition Failed; the client should reload the resource and decide again.
 */
public class StaleVersionException extends RuntimeException {

    private final long currentVersion;

    public StaleVersionException(String resource, long expectedVersion, long currentVersion) {
        super(resource + " was modified by someone else (expected version " + expectedVersion
                + ", current " + currentVersion + "); reload it and try again");
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
