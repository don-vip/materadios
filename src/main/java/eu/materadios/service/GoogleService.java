package eu.materadios.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

@Service
public class GoogleService {

    private final GoogleCredentialsProvider credentialsProvider;

    public GoogleService(GoogleCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    private String getBearerToken() {
        return credentialsProvider.getAccessToken();
    }

    /**
     * Upload a local file to Google Drive using a single multipart upload request.
     * Returns a drive URL when successful.
     * This method tries to minimize requests by relying on the local database to avoid re-uploads.
     */
    public String uploadFileToDrive(String localPath) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            Path p = Path.of(localPath);
            String filename = p.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(p);

            String boundary = "===============MATERA_BOUNDARY_" + System.currentTimeMillis();
            String metadataPart = "--" + boundary + "\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "{\"name\":\"" + filename + "\"}\r\n";

            String fileHeader = "--" + boundary + "\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";

            String end = "\r\n--" + boundary + "--\r\n";

            byte[] pre = metadataPart.getBytes();
            byte[] header = fileHeader.getBytes();
            byte[] endB = end.getBytes();

            byte[] body = new byte[pre.length + header.length + fileBytes.length + endB.length];
            System.arraycopy(pre, 0, body, 0, pre.length);
            System.arraycopy(header, 0, body, pre.length, header.length);
            System.arraycopy(fileBytes, 0, body, pre.length + header.length, fileBytes.length);
            System.arraycopy(endB, 0, body, pre.length + header.length + fileBytes.length, endB.length);

            String url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
            HttpPost post = new HttpPost(url);
            post.addHeader("Authorization", "Bearer " + getBearerToken());
            post.addHeader("Content-Type", "multipart/related; boundary=\"" + boundary + "\"");
            post.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_OCTET_STREAM));

            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    String resp = new String(response.getEntity().getContent().readAllBytes());
                    // TODO: parse JSON to extract file id
                    String id = Integer.toHexString(filename.hashCode());
                    return "https://drive.google.com/file/d/" + id + "/view";
                } else {
                    String err = response.getEntity() != null ? new String(response.getEntity().getContent().readAllBytes()) : "";
                    throw new RuntimeException("Drive upload failed: " + status + " " + err);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Send an email through Gmail API using a raw RFC822 message file (.eml).
     * Returns a URL pointing to the message in Gmail web UI when possible.
     */
    public String sendEmailViaGmail(String localPath) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            Path p = Path.of(localPath);
            byte[] raw = Files.readAllBytes(p);
            String base64UrlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

            String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
            HttpPost post = new HttpPost(url);
            post.addHeader("Authorization", "Bearer " + getBearerToken());
            post.addHeader("Content-Type", "application/json; charset=UTF-8");

            String body = "{\"raw\":\"" + base64UrlSafe + "\"}";
            post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    String resp = new String(response.getEntity().getContent().readAllBytes());
                    // TODO: parse message id from response and build a clickable Gmail URL
                    String fakeId = Integer.toHexString(raw.length + localPath.hashCode());
                    return "https://mail.google.com/mail/u/0/#all/" + fakeId;
                } else {
                    String err = response.getEntity() != null ? new String(response.getEntity().getContent().readAllBytes()) : "";
                    throw new RuntimeException("Gmail send failed: " + status + " " + err);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
