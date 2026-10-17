package com.edreams.mock.gamma;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edreams.mock.chaos.ChaosRegistry;
import com.edreams.mock.inventory.InventoryQuery;
import com.edreams.mock.inventory.ProviderId;
import com.edreams.mock.inventory.ProviderView;
import com.edreams.mock.inventory.ProviderViews;
import com.edreams.mock.web.ProviderMeta;

import jakarta.servlet.http.HttpServletResponse;

/** Mock-Gamma: streams its inventory as CSV, one row per segment. */
@RestController
@RequestMapping("/gamma")
public class GammaController {

    public static final String TEXT_CSV = "text/csv";

    private final ProviderView view;
    private final ChaosRegistry chaos;
    private final GammaCsvWriter writer;

    public GammaController(ProviderViews views, ChaosRegistry chaos, GammaCsvWriter writer) {
        this.view = views.get(ProviderId.GAMMA);
        this.chaos = chaos;
        this.writer = writer;
    }

    @GetMapping("/flights")
    public void flights(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String date,
            HttpServletResponse response) throws IOException {
        InventoryQuery query = InventoryQuery.of(origin, destination, date);
        response.setContentType(TEXT_CSV);
        response.setCharacterEncoding("UTF-8");
        try (OutputStream out = response.getOutputStream()) {
            writer.write(view.stream(query), out);
        }
    }

    @GetMapping(value = "/meta", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProviderMeta meta() {
        return ProviderMeta.of(view, chaos.get(ProviderId.GAMMA));
    }
}
