package com.edreams.mock.chaos;

import java.io.IOException;

import com.edreams.mock.inventory.ProviderId;

import jakarta.servlet.http.HttpServletResponse;

/** Writes the 500 response each provider would produce, in its own format. */
public final class ChaosErrorBodies {

    private ChaosErrorBodies() {
    }

    public static void write(ProviderId provider, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setCharacterEncoding("UTF-8");
        switch (provider) {
            case ALPHA -> {
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"upstream failure\",\"code\":500}");
            }
            case BETA -> {
                response.setContentType("application/xml");
                response.getWriter().write(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>500</Code><Message>upstream failure</Message></Error>");
            }
            case GAMMA -> {
                response.setContentType("text/plain");
                response.getWriter().write("ERROR 500 upstream failure");
            }
        }
        response.flushBuffer();
    }
}
