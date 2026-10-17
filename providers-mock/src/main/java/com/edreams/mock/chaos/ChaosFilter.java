package com.edreams.mock.chaos;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.edreams.mock.inventory.ProviderId;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies the provider's chaos settings before the first byte of a response: a uniform random delay,
 * then with the configured probability a 500 in the provider's own format. Only provider data paths
 * are affected; {@code /meta}, {@code /admin} and {@code /actuator} always answer immediately.
 */
@Component
public class ChaosFilter extends OncePerRequestFilter {

    public static final String DELAY_HEADER = "X-Mock-Delay-Ms";
    public static final String PROVIDER_HEADER = "X-Mock-Provider";

    private final ChaosRegistry registry;

    public ChaosFilter(ChaosRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return providerOf(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ProviderId provider = providerOf(request).orElseThrow();
        ChaosSettings settings = registry.get(provider);
        response.setHeader(PROVIDER_HEADER, provider.name());

        if (!settings.enabled()) {
            response.setHeader(DELAY_HEADER, "0");
            chain.doFilter(request, response);
            return;
        }

        int delay = settings.latencyMaxMs() > settings.latencyMinMs()
                ? ThreadLocalRandom.current().nextInt(settings.latencyMinMs(), settings.latencyMaxMs() + 1)
                : settings.latencyMinMs();
        response.setHeader(DELAY_HEADER, Integer.toString(delay));
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (settings.errorRate() > 0 && ThreadLocalRandom.current().nextDouble() < settings.errorRate()) {
            ChaosErrorBodies.write(provider, response);
            return;
        }
        chain.doFilter(request, response);
    }

    static Optional<ProviderId> providerOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.length() < 2 || path.endsWith("/meta")) {
            return Optional.empty();
        }
        int slash = path.indexOf('/', 1);
        String first = slash < 0 ? path.substring(1) : path.substring(1, slash);
        return ProviderId.fromPath(first);
    }
}
