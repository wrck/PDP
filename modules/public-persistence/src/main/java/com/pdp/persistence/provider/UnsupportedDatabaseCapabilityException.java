package com.pdp.persistence.provider;

public final class UnsupportedDatabaseCapabilityException extends IllegalStateException {

    public UnsupportedDatabaseCapabilityException(String message) {
        super(message);
    }
}
