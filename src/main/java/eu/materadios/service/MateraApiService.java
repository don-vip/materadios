package eu.materadios.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MateraApiService {
    private static final Logger log = LoggerFactory.getLogger(MateraApiService.class);

    private final MateraAuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

	@Value("${matera.api.url:https://api-core.matera.eu}")
	private String apiUrl;

    public MateraApiService(MateraAuthService authService) {
        this.authService = authService;
    }

    public List<Map<String, Object>> getBuildings() {
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
					.uri(URI.create(apiUrl + "/api/v1/buildings/40738"))
                    .header("Accept", "application/json")
                    .GET();
			String cookies = authService.getCookieHeader();
			if (cookies != null && !cookies.isBlank())
				rb.header("Cookie", cookies);

            HttpRequest req = rb.build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode root = mapper.readTree(resp.body());
                // expect results in data or results
                JsonNode results = root.path("data");
                if (results.isMissingNode() || results.isNull()) results = root.path("results");
                if (results.isMissingNode() || results.isNull()) results = root;
                if (results.isArray()) {
                    return mapper.convertValue(results, List.class);
                } else if (results.isObject()) {
                    // try to find "results" inside
                    JsonNode inner = results.path("results");
                    if (inner.isArray()) return mapper.convertValue(inner, List.class);
                    return Collections.singletonList(mapper.convertValue(results, Map.class));
                } else {
                    return Collections.emptyList();
                }
            } else {
                log.warn("Matera getBuildings failed: {} -> {}", resp.statusCode(), resp.body());
                throw new RuntimeException("Matera API returned status " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch buildings from Matera", e);
        }
    }
}
