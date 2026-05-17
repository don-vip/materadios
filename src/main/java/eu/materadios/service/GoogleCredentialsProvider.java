package eu.materadios.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleCredentialsProvider {

    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/gmail.send"
    );

    private GoogleCredentials credentials;
    private AccessToken cachedToken;

    private static final Duration MIN_TOKEN_LIFETIME = Duration.ofDays(30);

    public GoogleCredentialsProvider() {
        try {
            String path = System.getenv("GOOGLE_CREDENTIALS_PATH");
            if (path != null && !path.isBlank()) {
                // Best-effort: inspect JSON to see if it contains a long-lived access token
				byte[] raw = Files.readAllBytes(java.nio.file.Path.of(path));
                String text = new String(raw);
                ObjectMapper om = new ObjectMapper();
				Map<String, Object> m = om.readValue(text, new TypeReference<Map<String, Object>>() {
				});
				if (m.containsKey("access_token")) {
					Object tokenObj = m.get("access_token");
					Object expiryObj = m.get("expiry_date");
					Instant expiry = null;
					if (expiryObj instanceof Number) {
						expiry = Instant.ofEpochMilli(((Number) expiryObj).longValue());
					} else if (expiryObj instanceof String) {
						try {
							expiry = Instant.parse((String) expiryObj);
						} catch (Exception ex) {
							// ignore
                        }
					} else if (m.containsKey("expires_in")) {
						Number seconds = (Number) m.get("expires_in");
						expiry = Instant.now().plusSeconds(seconds.longValue());
					}

					if (expiry != null && expiry.isAfter(Instant.now().plus(MIN_TOKEN_LIFETIME))) {
						// token valid for at least 30 days
						this.cachedToken = new AccessToken((String) tokenObj, Date.from(expiry));
						return;
                    }
					// else fallthrough to try proper credential types
                }

                try (InputStream in = new FileInputStream(path)) {
                    GoogleCredentials creds = GoogleCredentials.fromStream(in);
                    if (creds instanceof ServiceAccountCredentials) {
                        ServiceAccountCredentials sac = (ServiceAccountCredentials) creds;
                        credentials = sac.createScoped(SCOPES);
                    } else {
                        credentials = creds.createScoped(SCOPES);
                    }
                }
            } else {
                // try GOOGLE_ACCESS_TOKEN env var fallback
                String token = System.getenv("GOOGLE_ACCESS_TOKEN");
                if (token != null && !token.isBlank()) {
                    // no expiry provided — treat as unacceptable (user requested 30-day min)
                    throw new IllegalStateException("GOOGLE_ACCESS_TOKEN provided without expiry; provide GOOGLE_CREDENTIALS_PATH with a long-lived token or a service account/refresh token.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Google credentials: " + e.getMessage(), e);
        }
    }

    public synchronized String getAccessToken() {
        try {
            if (cachedToken != null) {
                if (cachedToken.getExpirationTime() == null || Instant.ofEpochMilli(cachedToken.getExpirationTime().getTime()).isAfter(Instant.now().plusSeconds(60))) {
                    return cachedToken.getTokenValue();
                }
            }

            if (credentials != null) {
                // Refresh credentials when necessary. Service account credentials can mint new access tokens on demand.
                credentials.refreshIfExpired();
                AccessToken at = credentials.getAccessToken();
                if (at == null) {
                    credentials = credentials.createScoped(SCOPES);
                    credentials.refresh();
                    at = credentials.getAccessToken();
                }
                // if the returned access token expires sooner than MIN_TOKEN_LIFETIME, that's acceptable because service account private key allows re-minting
                cachedToken = at;
                return at.getTokenValue();
            }

            if (cachedToken != null) return cachedToken.getTokenValue();

            throw new IllegalStateException("No Google credentials configured. Set GOOGLE_CREDENTIALS_PATH to a service account JSON or to a JSON containing a long-lived access_token (>=30 days). For local/dev you can also use a refresh-token based JSON.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to refresh Google access token", e);
        }
    }
}
