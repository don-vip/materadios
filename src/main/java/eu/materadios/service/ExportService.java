package eu.materadios.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.materadios.api.Document;
import eu.materadios.api.DocumentFolder;
import eu.materadios.api.DocumentsResponse;
import eu.materadios.api.MailboxThread;
import eu.materadios.model.ExportedItem;
import eu.materadios.model.MailboxExportMetadata;
import eu.materadios.repository.ExportedItemRepository;
import eu.materadios.repository.ProjectLabelRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    // Cache: Matera folder ID → Drive folder ID, built lazily during document exports
    private final Map<Long, String> driveFolderCache = new HashMap<>();

    private static final DateTimeFormatter RFC_2822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final ExportedItemRepository repository;
    private final ProjectLabelRepository projectLabelRepository;
    private final MateraApiService materaApiService;
    private final GoogleService googleService;
    @Autowired
    private ExportService self;

    public ExportService(ExportedItemRepository repository, ProjectLabelRepository projectLabelRepository,
            MateraApiService materaApiService, GoogleService googleService) {
        this.repository = repository;
        this.projectLabelRepository = projectLabelRepository;
        this.materaApiService = materaApiService;
        this.googleService = googleService;
    }

    /**
     * TODO: Implement Matera API client to fetch all items and persist local files.
     */
    @Transactional
    public void exportAllFromMatera() {
        throw new UnsupportedOperationException("exportAllFromMatera is not implemented yet");
    }

    @Transactional
    public ExportedItem exportItemToGoogle(Long id) {
        Optional<ExportedItem> item = repository.findById(id);
        if (item.isEmpty())
            throw new IllegalArgumentException("Item not found");

        ExportedItem e = item.get();
        if (Boolean.TRUE.equals(e.getExported()))
            return e;

        String url;
        if ("DOCUMENT".equalsIgnoreCase(e.getType())) {
            url = exportDocumentToDrive(e);
        } else if ("EMAIL".equalsIgnoreCase(e.getType())) {
            url = exportEmailToGmail(e);
        } else {
            throw new IllegalArgumentException("Cannot export type " + e.getType() + " to Google");
        }
        e.setGoogleUrl(url);
        e.setExported(true);
        e.setExportedAt(Instant.now());
        repository.save(e);
        return e;
    }

    private String exportDocumentToDrive(ExportedItem e) {
        try {
            Document doc = new ObjectMapper().readValue(e.getMetadataJson(), Document.class);
            String parentFolderId = resolveDriveFolder(doc.folder_id());
            String resolvedUrl = doc.file() != null && doc.file().url() != null
                    ? materaApiService.resolveRedirect(doc.file().url()) : null;
            return googleService.uploadFileToDrive(e.getLocalPath(), parentFolderId, doc, resolvedUrl);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to upload document to Drive", ex);
        }
    }

    private synchronized String resolveDriveFolder(Long materaFolderId) {
        if (materaFolderId == null) {
            return googleService.getRootDriveFolderId();
        }
        if (driveFolderCache.containsKey(materaFolderId)) {
            return driveFolderCache.get(materaFolderId);
        }
        DocumentFolder folder = materaApiService.getDocumentFolder(materaFolderId);
        String parentDriveId = resolveDriveFolder(folder.parent_id());
        String driveFolderId = googleService.getOrCreateDriveFolder(folder.name(), parentDriveId);
        driveFolderCache.put(materaFolderId, driveFolderId);
        return driveFolderId;
    }

    @Transactional
    public void resetDocumentExported(Long id) {
        repository.findById(id).ifPresent(e -> {
            try {
                long docId = Long.parseLong(e.getMateraId().replace("document:", ""));
                Document doc = materaApiService.getDocument(docId);

                String folderSegment = doc.folder_id() != null ? String.valueOf(doc.folder_id()) : "root";
                Path dir = Paths.get("data", "exported", "documents", folderSegment);
                Files.createDirectories(dir);

                String baseName = sanitizeFilename(doc.name());
                String ext = extractExtension(doc.file());
                String filename = (ext.isEmpty() || baseName.toLowerCase().endsWith(ext)) ? baseName : baseName + ext;
                Path newPath = dir.resolve(filename).toAbsolutePath();

                // Delete old file if it has a different (bad) name
                Path oldPath = e.getLocalPath() != null ? Path.of(e.getLocalPath()) : null;
                if (oldPath != null && !oldPath.equals(newPath)) {
                    Files.deleteIfExists(oldPath);
                }

                byte[] bytes = materaApiService.downloadFile(doc.file().url());
                Files.write(newPath, bytes);

                e.setLocalPath(newPath.toString());
                e.setExported(false);
                e.setGoogleUrl(null);
                e.setExportedAt(null);
                repository.save(e);
                log.info("Reset document {} → {}", docId, newPath);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to re-download document for reset", ex);
            }
        });
    }

    private String exportEmailToGmail(ExportedItem e) {
        try {
            MailboxExportMetadata meta = new ObjectMapper().readValue(e.getMetadataJson(), MailboxExportMetadata.class);

            List<String> labelIds = new ArrayList<>();
            MailboxThread.Email email = meta.email();
            boolean isDraft = email != null && Boolean.TRUE.equals(email.draft());
            boolean isTrashed = isTrashed(meta.thread());

            if (isDraft) {
                labelIds.add("DRAFT");
            } else if (isTrashed) {
                labelIds.add("TRASH");
            } else {
                String kind = email != null ? email.kind() : null;
                if (kind != null && kind.contains("outbound")) {
                    labelIds.add("SENT");
                } else {
                    labelIds.add("INBOX");
                }
                Boolean read = meta.thread() != null ? meta.thread().read() : null;
                if (Boolean.FALSE.equals(read)) {
                    labelIds.add("UNREAD");
                }
            }

            // Always tag with the migration label
            labelIds.add(googleService.getMigrationLabelId());

            // Tag with the project label if the thread is linked to a project
            if (meta.thread() != null && meta.thread().project() != null) {
                long projectId = meta.thread().project().id();
                projectLabelRepository.findById(projectId)
                        .map(pl -> pl.getGmailLabelId())
                        .filter(id -> id != null && !id.isBlank())
                        .ifPresent(labelIds::add);
            }

            return googleService.insertEmailToGmail(e.getLocalPath(), labelIds);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to export email to Gmail", ex);
        }
    }

    public void resetAndReexportThread(long threadId) throws IOException {
        MailboxThread thread = materaApiService.getMailboxThread(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        // exportMailboxThread resets exported=false and regenerates EML files on disk
        self.exportMailboxThread(thread);
    }

    public void exportThreadToGoogle(long threadId) {
        String prefix = "mailbox_thread:" + threadId + ":email:";
        List<ExportedItem> emails = repository.findByMateraIdStartingWith(prefix).stream()
                .filter(e -> "EMAIL".equals(e.getType()))
                .sorted(Comparator.comparingLong(e -> Long.parseLong(e.getMateraId().split(":")[3])))
                .toList();
        if (emails.isEmpty()) {
            throw new IllegalArgumentException("No local emails found for thread " + threadId + " — export to disk first");
        }
        for (ExportedItem e : emails) {
            if (!Boolean.TRUE.equals(e.getExported())) {
                self.exportItemToGoogle(e.getId());
            }
        }
    }

    public List<ExportedItem> listAll() {
        return repository.findAll();
    }

    public ExportedItem findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<ExportedItem> listByRange(Instant start, Instant end) {
        return repository.findByCreatedAtBetween(start, end);
    }

    public void exportMailboxThreadToDisk(long threadId) throws IOException {
        MailboxThread thread = materaApiService.getMailboxThread(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        self.exportMailboxThread(thread);
    }

    @Transactional
    public void exportMailboxThread(MailboxThread thread) throws IOException {
        doExportMailboxThread(thread, false);
    }

    // onlyNew=true: skips emails already on disk and preserves their Gmail exported state.
    // Used by auto-export to handle new replies in existing threads.
    @Transactional
    public void exportMailboxThreadOnlyNew(MailboxThread thread) throws IOException {
        doExportMailboxThread(thread, true);
    }

    private void doExportMailboxThread(MailboxThread thread, boolean onlyNew) throws IOException {
        long threadId = thread.id();
        Path base = Paths.get("data", "exported", "mailbox_threads", String.valueOf(threadId));
        Files.createDirectories(base);

        ObjectMapper mapper = new ObjectMapper();
        List<MailboxThread.Email> emails = thread.emails();
        if (emails == null)
            return;

        for (MailboxThread.Email email : emails) {
            String emailMateraId = "mailbox_thread:" + threadId + ":email:" + email.id();
            if (onlyNew && repository.existsByMateraId(emailMateraId)) {
                continue; // already on disk — preserve its Gmail exported state
            }

            // Download attachments
            List<String> attLocalPaths = new ArrayList<>();
            List<MailboxThread.Attachment> downloadedAtts = new ArrayList<>();
            List<byte[]> downloadedContents = new ArrayList<>();

            List<MailboxThread.Attachment> atts = email.attachments();
            if (atts != null && !atts.isEmpty()) {
                Path attDir = base.resolve("email-" + email.id());
                Files.createDirectories(attDir);
                for (MailboxThread.Attachment att : atts) {
                    String filename = sanitizeFilename(att.name());
                    Path attFile = attDir.resolve(filename).toAbsolutePath();
                    try {
                        byte[] content = materaApiService.downloadFile(att.url().toString());
                        Files.write(attFile, content);
                        downloadedAtts.add(att);
                        downloadedContents.add(content);
                        attLocalPaths.add(attFile.toString());

                        String attMateraId = "mailbox_thread:" + threadId + ":email:" + email.id()
                                + ":attachment:" + att.id();
                        ExportedItem attItem = repository.findByMateraId(attMateraId)
                                .orElse(new ExportedItem());
                        attItem.setMateraId(attMateraId);
                        attItem.setType("EMAIL_ATTACHMENT");
                        attItem.setLocalPath(attFile.toString());
                        repository.save(attItem);
                    } catch (Exception ex) {
                        log.warn("Skipping attachment '{}' for email {}: {}", att.name(), email.id(),
                                ex.getMessage());
                    }
                }
            }

            // Generate MIME EML embedding all attachments
            Path file = base.resolve("email-" + email.id() + ".eml").toAbsolutePath();
            Files.write(file, buildEml(email, downloadedAtts, downloadedContents));

            ExportedItem it = repository.findByMateraId(emailMateraId).orElse(new ExportedItem());
            it.setMateraId(emailMateraId);
            it.setType("EMAIL");
            it.setLocalPath(file.toString());
            it.setMetadataJson(mapper.writeValueAsString(new MailboxExportMetadata(thread, email, attLocalPaths)));
            if (!onlyNew) {
                // Full re-export: reset Gmail state so the fresh EML gets re-imported
                it.setExported(false);
                it.setGoogleUrl(null);
                it.setExportedAt(null);
            }
            // onlyNew: new item starts with exported=null (treated as false) — ready for Gmail import
            repository.save(it);
        }
    }

    private static boolean isTrashed(MailboxThread thread) {
        if (thread == null) return false;
        String state = thread.state();
        return state != null && (state.contains("trash") || state.contains("delet"));
    }

    private static byte[] buildEml(MailboxThread.Email email, List<MailboxThread.Attachment> atts,
            List<byte[]> contents) {
        boolean hasAtts = !atts.isEmpty();
        String boundary = "==MATERA_" + email.id() + "_" + System.currentTimeMillis() + "==";
        StringBuilder sb = new StringBuilder();

        // RFC 2822 headers
        String fromAddr = email.from();
        String fromName = email.sender() != null ? email.sender().full_name() : null;
        if (fromName != null && fromAddr != null) {
            sb.append("From: ").append(rfc2047Encode(fromName)).append(" <").append(fromAddr).append(">\r\n");
        } else if (fromAddr != null) {
            sb.append("From: ").append(fromAddr).append("\r\n");
        } else if (fromName != null) {
            sb.append("From: ").append(rfc2047Encode(fromName)).append("\r\n");
        } else {
            sb.append("From: unknown@matera.eu\r\n");
        }
        if (email.recipients() != null && !email.recipients().isEmpty()) {
            sb.append("To: ").append(email.recipients().stream()
                    .map(r -> r.email() != null ? r.email() : r.full_name())
                    .collect(joining(", "))).append("\r\n");
        }
        sb.append("Subject: ").append(rfc2047Encode(email.subject() != null ? email.subject() : ""))
                .append("\r\n");
        sb.append("Date: ").append(
                email.date() != null ? email.date().format(RFC_2822)
                        : OffsetDateTime.now(ZoneOffset.UTC).format(RFC_2822))
                .append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Message-ID: <matera-").append(email.id()).append("@matera.eu>\r\n");
        if (email.references() != null && !email.references().isBlank()) {
            sb.append("References: ").append(email.references()).append("\r\n");
        }
        sb.append("X-Matera-Thread-Id: ").append(email.thread_id()).append("\r\n");
        sb.append("X-Matera-Message-Id: ").append(email.id()).append("\r\n");
        sb.append("X-Matera-Building-Id: ").append(email.building_id()).append("\r\n");

        if (hasAtts) {
            sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
            if (email.content_text() != null || email.content_html() != null) {
                sb.append("--").append(boundary).append("\r\n");
                appendBodyPart(sb, email);
            }
            for (int i = 0; i < atts.size(); i++) {
                MailboxThread.Attachment att = atts.get(i);
                byte[] content = contents.get(i);
                String ct = att.content_type() != null ? att.content_type() : "application/octet-stream";
                String fname = asciiFilename(att.name());
                sb.append("--").append(boundary).append("\r\n");
                sb.append("Content-Type: ").append(ct).append("; name=\"").append(fname).append("\"\r\n");
                sb.append("Content-Transfer-Encoding: base64\r\n");
                sb.append("Content-Disposition: attachment; filename=\"").append(fname).append("\"\r\n\r\n");
                sb.append(Base64.getMimeEncoder(76, "\r\n".getBytes(UTF_8)).encodeToString(content));
                sb.append("\r\n");
            }
            sb.append("--").append(boundary).append("--\r\n");
        } else {
            appendBodyPart(sb, email);
        }

        return sb.toString().getBytes(UTF_8);
    }

    private static void appendBodyPart(StringBuilder sb, MailboxThread.Email email) {
        byte[] body;
        String ct;
        if (email.content_text() != null) {
            body = email.content_text().getBytes(UTF_8);
            ct = "text/plain";
        } else if (email.content_html() != null) {
            body = email.content_html().getBytes(UTF_8);
            ct = "text/html";
        } else {
            return;
        }
        sb.append("Content-Type: ").append(ct).append("; charset=utf-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n\r\n");
        sb.append(Base64.getMimeEncoder(76, "\r\n".getBytes(UTF_8)).encodeToString(body));
        sb.append("\r\n");
    }

    // RFC 5322 specials that require quoting when used in a display name
    private static final String RFC5322_SPECIALS = "<>@,;:\\\"()";

    private static String rfc2047Encode(String value) {
        if (value == null)
            return "";
        for (char c : value.toCharArray()) {
            if (c > 127) {
                return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(UTF_8)) + "?=";
            }
        }
        // Pure ASCII but contains RFC 5322 specials → quoted-string
        for (char c : value.toCharArray()) {
            if (RFC5322_SPECIALS.indexOf(c) >= 0) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return value;
    }

    private static String asciiFilename(String name) {
        if (name == null)
            return "attachment";
        return name.replaceAll("[^\\x20-\\x7E]", "_").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public void exportDocumentToDisk(long documentId) throws IOException {
        if (repository.existsByMateraId("document:" + documentId)) {
            log.info("Document {} already exported, skipping", documentId);
            return;
        }
        Document doc = materaApiService.getDocument(documentId);
        self.saveDocumentExport(doc);
    }

    public void exportDocumentFolderToDisk(long folderId) throws IOException {
        exportFolderRecursive(folderId);
    }

    private void exportFolderRecursive(long folderId) throws IOException {
        String after = null;
        do {
            DocumentsResponse resp = materaApiService.getDocuments(folderId, after);
            for (Document doc : resp.results()) {
                if (!repository.existsByMateraId("document:" + doc.id())) {
                    self.saveDocumentExport(doc);
                }
            }
            after = resp.meta().has_next_page() ? resp.meta().end_cursor() : null;
        } while (after != null);

        for (DocumentFolder sub : materaApiService.getDocumentFolders(folderId)) {
            exportFolderRecursive(sub.id());
        }
    }

    @Transactional
    public void saveDocumentExport(Document doc) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        String folderSegment = doc.folder_id() != null ? String.valueOf(doc.folder_id()) : "root";
        Path dir = Paths.get("data", "exported", "documents", folderSegment);
        Files.createDirectories(dir);

        String baseName = sanitizeFilename(doc.name());
        String ext = extractExtension(doc.file());
        String filename = (ext.isEmpty() || baseName.toLowerCase().endsWith(ext)) ? baseName : baseName + ext;
        Path filePath = dir.resolve(filename).toAbsolutePath();

        byte[] bytes = materaApiService.downloadFile(doc.file().url());
        Files.write(filePath, bytes);

        ExportedItem item = new ExportedItem();
        item.setMateraId("document:" + doc.id());
        item.setType("DOCUMENT");
        item.setLocalPath(filePath.toString());
        item.setMetadataJson(mapper.writeValueAsString(doc));
        repository.save(item);

        log.info("Exported document {} to {}", doc.id(), filePath);
    }

    public Set<Long> exportedDocumentIds() {
        return repository.findByMateraIdStartingWith("document:").stream()
                .map(it -> Long.parseLong(it.getMateraId().substring("document:".length())))
                .collect(toSet());
    }

    private static String sanitizeFilename(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        s = s.replaceAll("_+", "_").strip();
        s = s.replaceAll("^[.]+|[.]+$", "");
        return s.isEmpty() ? "unnamed" : s;
    }

    private static String extractExtension(Document.DocumentFile file) {
        if (file == null || file.url() == null) {
            return "";
        }
        String path = file.url().split("\\?")[0];
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            String segment = URLDecoder.decode(path.substring(lastSlash + 1), UTF_8);
            int dot = segment.lastIndexOf('.');
            if (dot >= 0) {
                return segment.substring(dot).toLowerCase();
            }
        }
        if (file.content_type() != null) {
            return switch (file.content_type().split(";")[0].trim().toLowerCase()) {
                case "application/pdf" -> ".pdf";
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                default -> "";
            };
        }
        return "";
    }

    @Transactional
    public void purgeDatabase() {
        repository.deleteAll();
    }

    public void purgeLocalFiles() throws IOException {
        Path exported = Paths.get("data", "exported");
        if (Files.exists(exported)) {
            deleteRecursively(exported);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }

    public boolean isThreadOnDisk(long threadId) {
        return repository.findByMateraIdStartingWith("mailbox_thread:" + threadId + ":email:").stream()
                .anyMatch(e -> "EMAIL".equals(e.getType()));
    }

    public boolean isEmailOnDisk(long threadId, long emailId) {
        return repository.existsByMateraId("mailbox_thread:" + threadId + ":email:" + emailId);
    }

    public Set<Long> exportedMailboxThreadIds() {
        List<ExportedItem> items = repository.findByMateraIdStartingWith("mailbox_thread:");
        Set<Long> s = new TreeSet<>();
        for (ExportedItem it : items) {
            // pattern: mailbox_thread:{threadId}:email:{emailId}
            s.add(Long.parseLong(it.getMateraId().split(":")[1]));
        }
        return s;
    }
}
