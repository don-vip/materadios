package eu.materadios.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GoogleCredentialsProviderTest {

    @Test
    public void noCredentials_getAccessToken_throws() {
        GoogleCredentialsProvider provider = new GoogleCredentialsProvider();
        assertThrows(IllegalStateException.class, provider::getAccessToken);
    }
}
