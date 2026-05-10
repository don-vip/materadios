package eu.materadios.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MateraAuthService {

    private static final Logger log = LoggerFactory.getLogger(MateraAuthService.class);

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${matera.username:}")
    private String username;

    @Value("${matera.password:}")
    private String password;

    @Value("${matera.base.url:https://app.matera.eu}")
    private String baseUrl;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<String> refreshToken = new AtomicReference<>();
    private volatile Instant accessTokenExpiry = Instant.EPOCH;

    public synchronized String getAccessToken() {
        if (accessToken.get() == null || Instant.now().isAfter(accessTokenExpiry.minusSeconds(30))) {
            log.info("Access token missing or expired, performing login to Matera");
            loginWithCredentials();
        }
        return accessToken.get();
    }

    private void loginWithCredentials() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Matera credentials (MATERA_USERNAME/MATERA_PASSWORD) are not configured");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            Map<String, String> user = new HashMap<>();
            user.put("email", username);
            user.put("password", password);
            body.put("user", user);

            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/_public/users/sign_in"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                parseAndStoreTokens(resp.body());
            } else {
                log.warn("Matera login failed: status {} body={}", resp.statusCode(), resp.body());
                throw new RuntimeException("Matera login failed with status " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Matera login failed", e);
        }
    }

    private void parseAndStoreTokens(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        // Try common locations for tokens
        JsonNode access = root.path("data").path("access_token");
        if (access.isMissingNode() || access.isNull()) access = root.path("access_token");
        JsonNode refresh = root.path("data").path("refresh_token");
        if (refresh.isMissingNode() || refresh.isNull()) refresh = root.path("refresh_token");

        if (!access.isMissingNode() && !access.isNull()) {
            accessToken.set(access.asText());
        }
        if (!refresh.isMissingNode() && !refresh.isNull()) {
            refreshToken.set(refresh.asText());
        }

        // expires_in may be present
        JsonNode expiresIn = root.path("data").path("expires_in");
        if (expiresIn.isMissingNode() || expiresIn.isNull()) expiresIn = root.path("expires_in");
        if (!expiresIn.isMissingNode() && !expiresIn.isNull()) {
            long seconds = expiresIn.asLong();
            accessTokenExpiry = Instant.now().plusSeconds(seconds);
        } else {
            // fallback: set a default short lifetime (1 hour)
            accessTokenExpiry = Instant.now().plusSeconds(3600);
        }

        log.info("Matera login succeeded, access token length={}, hasRefresh={}",
                accessToken.get() == null ? 0 : accessToken.get().length(), refreshToken.get() != null);
    }

    // naive refresh: just re-login with credentials to obtain new tokens
    public synchronized void refreshAccessToken() {
        log.info("Refreshing Matera access token by re-authenticating");
        loginWithCredentials();
    }

}
