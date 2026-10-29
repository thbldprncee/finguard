package com.finguard.transaction.controller;

/**
 * Formats of the request headers the API accepts, used in {@code @Pattern} constraints.
 * The optional headers allow an empty value, which is treated as "not sent".
 */
public final class HeaderPatterns {

    public static final String IDEMPOTENCY_KEY = "^([A-Za-z0-9_-]{8,100})?$";
    public static final String DEVICE_ID = "^([A-Za-z0-9_-]{8,100})?$";
    /** Same format the gateway enforces; services check again rather than trust the network. */
    public static final String CORRELATION_ID = "^([A-Za-z0-9-]{8,64})?$";
    /** Set by the gateway from the connection: IPv4 or IPv6 (optionally with a zone id). */
    public static final String CLIENT_IP = "^([0-9A-Za-z:.%]{2,45})?$";

    private HeaderPatterns() {
    }
}
