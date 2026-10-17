package com.edreams.flights.infrastructure.provider.http;

import com.edreams.flights.domain.model.ProviderId;

/** The provider answered with a non-2xx status before any data was streamed. */
public class ProviderCallException extends RuntimeException {

    private final ProviderId provider;
    private final int status;

    public ProviderCallException(ProviderId provider, int status) {
        super(provider + " responded HTTP " + status);
        this.provider = provider;
        this.status = status;
    }

    public ProviderId provider() {
        return provider;
    }

    public int status() {
        return status;
    }

    public boolean serverError() {
        return status >= 500;
    }
}
