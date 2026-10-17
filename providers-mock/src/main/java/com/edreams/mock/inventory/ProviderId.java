package com.edreams.mock.inventory;

import java.util.Optional;

/** The three simulated GDS providers. */
public enum ProviderId {
    ALPHA("alpha", "ALPHA-"),
    BETA("beta", "BETA-"),
    GAMMA("gamma", "GAMMA-");

    private final String path;
    private final String offerPrefix;

    ProviderId(String path, String offerPrefix) {
        this.path = path;
        this.offerPrefix = offerPrefix;
    }

    /** First URL path segment, for example {@code alpha}. */
    public String path() {
        return path;
    }

    public String offerPrefix() {
        return offerPrefix;
    }

    public static Optional<ProviderId> fromPath(String path) {
        for (ProviderId provider : values()) {
            if (provider.path.equalsIgnoreCase(path)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
