package eu.materadios.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles the Google OAuth 2.0 authorization flow to obtain a refresh token.
 * Navigate to /auth/google to start. On success the credentials file is
 * overwritten with a valid authorized_user JSON containing the new refresh
 * token.
 *
 * Prerequisites:
 *   1. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env
 *   2. Add http://localhost:8080/auth/google/callback to the OAuth client's
 *      authorized redirect URIs in Google Cloud Console.
 */
@Controller
@RequestMapping("/auth/google")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/gmail.insert",
            "https://www.googleapis.com/auth/gmail.labels");

    private static final String REDIRECT_URI = "http://localhost:8080/auth/google/callback";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @GetMapping
    public String startAuth(Model model) {
        String clientId = System.getProperty("GOOGLE_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            model.addAttribute("error", "GOOGLE_CLIENT_ID not set in .env");
            return "auth_google";
        }

        String scopeParam = URLEncoder.encode(String.join(" ", SCOPES), StandardCharsets.UTF_8);
        String url = AUTH_URL
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + scopeParam
                + "&access_type=offline"
                + "&prompt=consent";

        return "redirect:" + url;
    }

    @GetMapping("/callback")
    public String callback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        if (error != null) {
            model.addAttribute("error", "Google returned an error: " + error);
            return "auth_google";
        }
        if (code == null || code.isBlank()) {
            model.addAttribute("error", "No authorization code received from Google.");
            return "auth_google";
        }

        String clientId = System.getProperty("GOOGLE_CLIENT_ID");
        String clientSecret = System.getProperty("GOOGLE_CLIENT_SECRET");
        String credPath = System.getProperty("GOOGLE_CREDENTIALS_PATH");

        if (clientId == null || clientSecret == null) {
            model.addAttribute("error", "GOOGLE_CLIENT_ID or GOOGLE_CLIENT_SECRET not set in .env");
            return "auth_google";
        }

        try {
            // Exchange authorization code for tokens
            String body = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, BodyHandlers.ofString());
            Map<String, Object> tokens = mapper.readValue(resp.body(), new TypeReference<Map<String, Object>>() {});

            if (tokens.containsKey("error")) {
                model.addAttribute("error", "Token exchange failed: " + tokens.get("error")
                        + " — " + tokens.getOrDefault("error_description", ""));
                return "auth_google";
            }

            String refreshToken = (String) tokens.get("refresh_token");
            if (refreshToken == null) {
                model.addAttribute("error",
                        "No refresh_token in Google response. Make sure prompt=consent is set and the app is authorized with access_type=offline.");
                return "auth_google";
            }

            // Build authorized_user credentials JSON
            Map<String, String> creds = Map.of(
                    "type", "authorized_user",
                    "client_id", clientId,
                    "client_secret", clientSecret,
                    "refresh_token", refreshToken);

            String credsJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(creds);

            // Save to the configured path or next to the app
            Path savePath = credPath != null && !credPath.isBlank()
                    ? Path.of(credPath)
                    : Path.of("google_credentials.json");
            Files.writeString(savePath, credsJson);
            log.info("Google credentials saved to {}", savePath.toAbsolutePath());

            model.addAttribute("success", "Credentials saved to " + savePath.toAbsolutePath()
                    + ". Restart the application to apply them.");
            model.addAttribute("credPath", savePath.toAbsolutePath().toString());
        } catch (IOException | InterruptedException ex) {
            log.error("OAuth callback failed", ex);
            model.addAttribute("error", ex.getMessage());
        }

        return "auth_google";
    }
}
