package eu.materadios.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GoogleCredentialsProviderTest {

    @Test
    public void noCredentialsPath_constructor_throwsIllegalState() {
        // GOOGLE_CREDENTIALS_PATH is not set in the test JVM, so the constructor must fail fast
        System.clearProperty("GOOGLE_CREDENTIALS_PATH");
        assertThrows(IllegalStateException.class, GoogleCredentialsProvider::new);
    }

    @Test
    public void missingCredentialsFile_constructor_throwsRuntime() {
        System.setProperty("GOOGLE_CREDENTIALS_PATH", "/nonexistent/credentials.json");
        try {
            assertThrows(RuntimeException.class, GoogleCredentialsProvider::new);
        } finally {
            System.clearProperty("GOOGLE_CREDENTIALS_PATH");
        }
    }
}
