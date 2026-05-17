package eu.materadios.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;

@Component
public class GoogleCredentialsProvider {

    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/gmail.insert",
            "https://www.googleapis.com/auth/gmail.labels");

    private final GoogleCredentials credentials;

    public GoogleCredentialsProvider() {
        String path = System.getProperty("GOOGLE_CREDENTIALS_PATH");
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_CREDENTIALS_PATH not set in .env. Navigate to /auth/google to generate credentials.");
        }
        try (var in = new FileInputStream(path)) {
            credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Google credentials from " + path
                    + ". If the file is missing, navigate to /auth/google to regenerate it.", e);
        }
    }

    public synchronized String getAccessToken() {
        try {
            credentials.refreshIfExpired();
            var token = credentials.getAccessToken();
            if (token == null) {
                credentials.refresh();
                token = credentials.getAccessToken();
            }
            return token.getTokenValue();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to refresh Google access token. Navigate to /auth/google to re-authorize.", e);
        }
    }
}
