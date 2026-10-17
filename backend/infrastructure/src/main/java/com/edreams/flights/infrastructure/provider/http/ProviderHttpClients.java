package com.edreams.flights.infrastructure.provider.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.edreams.flights.domain.model.ProviderId;

/** Builds the streaming HTTP client every provider adapter uses. */
public final class ProviderHttpClients {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private ProviderHttpClients() {
    }

    /**
     * {@code streamTimeout} bounds the whole attempt: Spring's JDK factory applies the read timeout to
     * the body as well as the headers, so a value shorter than a full inventory stream cuts it off.
     */
    public static RestClient build(String baseUrl, Duration streamTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(streamTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Body consumer that may throw IO errors; they surface as Spring's ResourceAccessException and are retried. */
    @FunctionalInterface
    public interface BodyReader {
        void read(InputStream body) throws IOException;
    }

    /** GETs {@code /flights} and hands the open body stream to the reader; the response is closed afterwards. */
    public static void stream(RestClient client, ProviderId provider, MediaType accept, BodyReader reader) {
        client.get()
                .uri("/flights")
                .accept(accept)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new ProviderCallException(provider, response.getStatusCode().value());
                    }
                    try (InputStream body = response.getBody()) {
                        reader.read(body);
                    }
                    return null;
                }, true);
    }
}
