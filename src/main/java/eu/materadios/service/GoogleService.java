package eu.materadios.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleService {

    private static final String GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";

    @Value("${gmail.labels.projects-parent}")
    private String projectsParentLabel;

    @Value("${gmail.labels.migration}")
    private String migrationLabelName;

    private volatile String migrationLabelId;

    private final GoogleCredentialsProvider credentialsProvider;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient javaHttp = HttpClient.newHttpClient();

    public GoogleService(GoogleCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public String getProjectsParentLabel() {
        return projectsParentLabel;
    }

    /** Returns the Gmail label ID for the migration label, resolved once and cached. */
    public String getMigrationLabelId() {
        if (migrationLabelId == null) {
            synchronized (this) {
                if (migrationLabelId == null) {
                    String id = listGmailLabels().get(migrationLabelName);
                    if (id == null) {
                        throw new IllegalStateException(
                                "Gmail label '" + migrationLabelName + "' not found. "
                                + "Create it in Gmail and set gmail.labels.migration in application.properties.");
                    }
                    migrationLabelId = id;
                }
            }
        }
        return migrationLabelId;
    }

    private String getBearerToken() {
        return credentialsProvider.getAccessToken();
    }

    /** Returns a map of label name → label id for all Gmail labels. */
    public Map<String, String> listGmailLabels() {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(GMAIL_BASE + "/labels"))
                    .header("Authorization", "Bearer " + getBearerToken())
                    .GET().build();
            var resp = javaHttp.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException("Gmail labels list failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            Map<String, String> result = new HashMap<>();
            for (JsonNode label : root.path("labels")) {
                result.put(label.path("name").asString(), label.path("id").asString());
            }
            return result;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to list Gmail labels", e);
        }
    }

    /**
     * Creates a Gmail label and returns its id.
     * Sub-labels are expressed as "Parent/Child" — Gmail handles the nesting.
     */
    public String createGmailLabel(String name) {
        try {
            String body = mapper.writeValueAsString(Map.of("name", name));
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(GMAIL_BASE + "/labels"))
                    .header("Authorization", "Bearer " + getBearerToken())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = javaHttp.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException("Gmail label create failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode node = mapper.readTree(resp.body());
            return node.path("id").asString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to create Gmail label: " + name, e);
        }
    }

    /**
     * Ensures the label "{projectsParentLabel}/{projectTitle}" exists.
     * Returns the Gmail label id (existing or newly created).
     */
    public String ensureProjectLabel(String projectTitle) {
        String labelName = projectsParentLabel + "/" + sanitizeLabelName(projectTitle);
        Map<String, String> existing = listGmailLabels();
        if (existing.containsKey(labelName)) {
            return existing.get(labelName);
        }
        return createGmailLabel(labelName);
    }

    public String buildProjectLabelName(String projectTitle) {
        return projectsParentLabel + "/" + sanitizeLabelName(projectTitle);
    }

    private static String sanitizeLabelName(String title) {
        // Gmail label names must not contain / (reserved as hierarchy separator)
        return title == null ? "Sans titre" : title.replace("/", "-").strip();
    }

    /**
     * Upload a local file to Google Drive using a single multipart upload request.
     * Returns a drive URL when successful. This method tries to minimize requests
     * by relying on the local database to avoid re-uploads.
     */
    public String uploadFileToDrive(String localPath) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            Path p = Path.of(localPath);
            String filename = p.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(p);

            String boundary = "===============MATERA_BOUNDARY_" + System.currentTimeMillis();
            String metadataPart = "--" + boundary + "\r\n" + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                    + "{\"name\":\"" + filename + "\"}\r\n";

            String fileHeader = "--" + boundary + "\r\n" + "Content-Type: application/octet-stream\r\n\r\n";

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
                    String err = response.getEntity() != null
                            ? new String(response.getEntity().getContent().readAllBytes())
                            : "";
                    throw new RuntimeException("Drive upload failed: " + status + " " + err);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Import a historical email into Gmail using messages.import.
     * The Date: header in the EML sets the Gmail internal date.
     * Labels control inbox/sent/unread placement.
     */
    public String insertEmailToGmail(String emlPath, List<String> labelIds) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            byte[] rawMessage = Files.readAllBytes(Path.of(emlPath));
            String base64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(rawMessage);

            StringBuilder json = new StringBuilder("{\"raw\":\"").append(base64Url).append("\"");
            if (labelIds != null && !labelIds.isEmpty()) {
                json.append(",\"labelIds\":[");
                for (int i = 0; i < labelIds.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(labelIds.get(i)).append("\"");
                }
                json.append("]");
            }
            json.append("}");

            HttpPost post = new HttpPost(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/import"
                    + "?internalDateSource=dateHeader&neverMarkSpam=true");
            post.addHeader("Authorization", "Bearer " + getBearerToken());
            post.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));

            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
                int status = response.getCode();
                String resp = response.getEntity() != null
                        ? new String(response.getEntity().getContent().readAllBytes())
                        : "";
                if (status >= 200 && status < 300) {
                    String messageId = extractJsonString(resp, "id");
                    boolean isSent = labelIds != null && labelIds.contains("SENT");
                    return "https://mail.google.com/mail/u/0/#" + (isSent ? "sent" : "inbox") + "/" + messageId;
                } else {
                    throw new RuntimeException("Gmail import failed: " + status + " " + resp);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "";
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
                    String messageId = extractJsonString(resp, "id");
                    return "https://mail.google.com/mail/u/0/#sent/" + messageId;
                } else {
                    String err = response.getEntity() != null
                            ? new String(response.getEntity().getContent().readAllBytes())
                            : "";
                    throw new RuntimeException("Gmail send failed: " + status + " " + err);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
