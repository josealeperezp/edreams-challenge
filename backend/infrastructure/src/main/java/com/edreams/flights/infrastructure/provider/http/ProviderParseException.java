package com.edreams.flights.infrastructure.provider.http;

/** The response body is not in the provider's contract at all (not JSON, wrong root element, bad header). Not retried. */
public class ProviderParseException extends RuntimeException {

    public ProviderParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProviderParseException(String message) {
        super(message);
    }
}
