package eu.materadios.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.materadios.api.Document;

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
    private static final String DRIVE_BASE = "https://www.googleapis.com/drive/v3";

    @Value("${google.drive.root-folder:Matera-export}")
    private String driveRootFolderName;

    private volatile String driveRootFolderId;

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

    /** Returns the Drive folder ID for the configured root folder, creating it if needed. */
    public String getRootDriveFolderId() {
        if (driveRootFolderId == null) {
            synchronized (this) {
                if (driveRootFolderId == null) {
                    driveRootFolderId = getOrCreateDriveFolder(driveRootFolderName, null);
                }
            }
        }
        return driveRootFolderId;
    }

    /**
     * Finds a Drive folder with the given name inside parentId (or at root if parentId is null).
     * Creates it if it does not exist. Returns the Drive folder ID.
     */
    public String getOrCreateDriveFolder(String name, String parentId) {
        String existing = findDriveFolder(name, parentId);
        return existing != null ? existing : createDriveFolder(name, parentId);
    }

    private String findDriveFolder(String name, String parentId) {
        try {
            String parent = parentId != null ? parentId : "root";
            String q = "name='" + name.replace("'", "\\'") + "'"
                    + " and mimeType='application/vnd.google-apps.folder'"
                    + " and '" + parent + "' in parents"
                    + " and trashed=false";
            String url = DRIVE_BASE + "/files?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + "&fields=files(id)";
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + getBearerToken())
                    .GET().build();
            var resp = javaHttp.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException("Drive folder search failed: " + resp.statusCode() + " " + resp.body());
            }
            JsonNode files = mapper.readTree(resp.body()).path("files");
            return files.isArray() && files.size() > 0 ? files.get(0).path("id").asText() : null;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to search Drive folder: " + name, e);
        }
    }

    private String createDriveFolder(String name, String parentId) {
        try {
            String body = parentId != null
                    ? "{\"name\":\"" + name + "\",\"mimeType\":\"application/vnd.google-apps.folder\""
                            + ",\"parents\":[\"" + parentId + "\"]}"
                    : "{\"name\":\"" + name + "\",\"mimeType\":\"application/vnd.google-apps.folder\"}";
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(DRIVE_BASE + "/files"))
                    .header("Authorization", "Bearer " + getBearerToken())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var resp = javaHttp.send(req, BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException("Drive folder create failed: " + resp.statusCode() + " " + resp.body());
            }
            return mapper.readTree(resp.body()).path("id").asText();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to create Drive folder: " + name, e);
        }
    }

    /**
     * Upload a local file to Google Drive inside the given parent folder.
     * Persists Matera metadata (id, kind, dates, original URL) in the Drive file's
     * description and custom properties. Sets modifiedTime to the Matera updated_at date.
     * Returns the canonical drive.google.com URL for the uploaded file.
     */
    public String uploadFileToDrive(String localPath, String parentFolderId, Document doc, String resolvedFileUrl) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            Path p = Path.of(localPath);
            String filename = p.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(p);

            // Build metadata as a proper map to avoid manual JSON escaping
            Map<String, Object> fileMeta = new LinkedHashMap<>();
            fileMeta.put("name", filename);
            fileMeta.put("parents", List.of(parentFolderId));
            if (doc != null) {
                fileMeta.put("description", buildDriveDescription(doc, resolvedFileUrl));
                if (doc.updated_at() != null) {
                    fileMeta.put("modifiedTime",
                            doc.updated_at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
                fileMeta.put("properties", buildDriveProperties(doc, resolvedFileUrl));
            }

            String boundary = "===============MATERA_BOUNDARY_" + System.currentTimeMillis();
            String metadata = mapper.writeValueAsString(fileMeta);
            String metadataPart = "--" + boundary + "\r\n"
                    + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                    + metadata + "\r\n";
            String fileHeader = "--" + boundary + "\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            String end = "\r\n--" + boundary + "--\r\n";

            byte[] pre = metadataPart.getBytes();
            byte[] header = fileHeader.getBytes();
            byte[] endB = end.getBytes();
            byte[] body = new byte[pre.length + header.length + fileBytes.length + endB.length];
            System.arraycopy(pre, 0, body, 0, pre.length);
            System.arraycopy(header, 0, body, pre.length, header.length);
            System.arraycopy(fileBytes, 0, body, pre.length + header.length, fileBytes.length);
            System.arraycopy(endB, 0, body, pre.length + header.length + fileBytes.length, endB.length);

            HttpPost post = new HttpPost(
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart");
            post.addHeader("Authorization", "Bearer " + getBearerToken());
            post.addHeader("Content-Type", "multipart/related; boundary=\"" + boundary + "\"");
            post.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_OCTET_STREAM));

            try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
                int status = response.getCode();
                if (status >= 200 && status < 300) {
                    String resp = new String(response.getEntity().getContent().readAllBytes());
                    String fileId = extractJsonString(resp, "id");
                    return "https://drive.google.com/file/d/" + fileId + "/view";
                } else {
                    String err = response.getEntity() != null
                            ? new String(response.getEntity().getContent().readAllBytes()) : "";
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

    private static String buildDriveDescription(Document doc, String resolvedFileUrl) {
        StringBuilder sb = new StringBuilder("Imported from Matera");
        sb.append("\nID: ").append(doc.id());
        if (doc.kind() != null) sb.append(" | Kind: ").append(doc.kind());
        if (doc.folder_id() != null) sb.append(" | Folder ID: ").append(doc.folder_id());
        if (doc.created_at() != null) sb.append("\nCreated: ").append(doc.created_at());
        if (doc.updated_at() != null) sb.append("\nModified: ").append(doc.updated_at());
        // Matera API URL (stable, no query params on this one)
        if (doc.file() != null && doc.file().url() != null) {
            sb.append("\nMatera URL: ").append(doc.file().url());
        }
        // Resolved storage URL — strip expiring signature params, keep the stable object path
        if (resolvedFileUrl != null && !resolvedFileUrl.equals(
                doc.file() != null ? doc.file().url() : null)) {
            int q = resolvedFileUrl.indexOf('?');
            sb.append("\nStorage URL: ").append(q > 0 ? resolvedFileUrl.substring(0, q) : resolvedFileUrl);
        }
        return sb.toString();
    }

    private static Map<String, String> buildDriveProperties(Document doc, String resolvedFileUrl) {
        // Drive property limit: key + value ≤ 124 bytes (UTF-8) per entry
        Map<String, String> props = new LinkedHashMap<>();
        props.put("matera_id", String.valueOf(doc.id()));
        if (doc.kind() != null) props.put("matera_kind", doc.kind());
        if (doc.folder_id() != null) props.put("matera_folder_id", String.valueOf(doc.folder_id()));
        if (doc.created_at() != null)
            props.put("matera_created_at", doc.created_at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (doc.updated_at() != null)
            props.put("matera_updated_at", doc.updated_at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (doc.file() != null && doc.file().created_at() != null)
            props.put("matera_file_at", doc.file().created_at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        // S3/storage object path without expiring query params (≤ 124 bytes with key)
        if (resolvedFileUrl != null) {
            int q = resolvedFileUrl.indexOf('?');
            String storageKey = q > 0 ? resolvedFileUrl.substring(0, q) : resolvedFileUrl;
            if (storageKey.length() <= 111) { // "matera_s3_key" = 13 bytes, 124 - 13 = 111
                props.put("matera_s3_key", storageKey);
            }
        }
        return props;
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
