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
import java.util.List;
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
import tools.jackson.databind.ObjectMapper;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ExportedItemRepository repository;
    private final MateraApiService materaApiService;
    private final GoogleService googleService;
    @Autowired
    private ExportService self;

    public ExportService(ExportedItemRepository repository, MateraApiService materaApiService,
            GoogleService googleService) {
        this.repository = repository;
        this.materaApiService = materaApiService;
        this.googleService = googleService;
    }

    /**
     * TODO: Implement Matera API client to fetch all items and persist local files.
     */
    @Transactional
    public void exportAllFromMatera() {
        // TODO: implement Matera API integration
        throw new UnsupportedOperationException("exportAllFromMatera is not implemented yet");
    }

    @Transactional
    public ExportedItem exportItemToGoogle(Long id) {
        Optional<ExportedItem> item = repository.findById(id);
        if (item.isEmpty())
            throw new IllegalArgumentException("Item not found");

        ExportedItem e = item.get();
        if (Boolean.TRUE.equals(e.getExported()))
            return e; // already exported

        // Upload depending on type
        String url;
        if ("DOCUMENT".equalsIgnoreCase(e.getType())) {
            url = googleService.uploadFileToDrive(e.getLocalPath());
        } else {
            url = googleService.sendEmailViaGmail(e.getLocalPath());
        }
        e.setGoogleUrl(url);
        e.setExported(true);
        e.setExportedAt(Instant.now());
        repository.save(e);
        return e;
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

    /**
     * Export a mailbox thread and its emails to local disk. Creates one
     * ExportedItem per email.
     *
     * @throws IOException
     */
    public void exportMailboxThreadToDisk(long threadId) throws IOException {
        MailboxThread thread = materaApiService.getMailboxThread(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found: " + threadId);
        }
        self.exportMailboxThread(thread);
    }

    /**
     * Performs DB writes for a mailbox thread export. This method is transactional.
     */
    @Transactional
    public void exportMailboxThread(MailboxThread thread) throws IOException {
        long threadId = thread.id();

        Path base = Paths.get("data", "exported", "mailbox_threads", String.valueOf(threadId));
        Files.createDirectories(base);

        ObjectMapper mapper = new ObjectMapper();

        List<MailboxThread.Email> emails = thread.emails();
        if (emails == null)
            return;

        for (MailboxThread.Email email : emails) {
            String fileName = "email-" + email.id() + ".eml";
            Path file = base.resolve(fileName).toAbsolutePath();
            String body = email.content_text() != null ? email.content_text() : email.content_html();
            if (body == null) {
                body = "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("From: ").append(
                    email.from() != null ? email.from() : (email.sender() != null ? email.sender().full_name() : ""))
                    .append('\n');
            sb.append("To: ");
            if (email.recipients() != null) {
                sb.append(email.recipients().stream().map(r -> r.email() != null ? r.email() : r.full_name())
                        .collect(joining(",")));
            }
            sb.append('\n');
            sb.append("Subject: ").append(email.subject() != null ? email.subject() : "").append('\n');
            sb.append("Date: ").append(email.date() != null ? email.date().toString() : "").append('\n');
            sb.append("MIME-Version: 1.0\n");
            sb.append("Content-Type: text/plain; charset=utf-8\n\n");
            sb.append(body).append('\n');

            Files.write(file, sb.toString().getBytes(UTF_8));

            ExportedItem it = new ExportedItem();
            it.setMateraId("mailbox_thread:" + threadId + ":email:" + email.id());
            it.setType("EMAIL");
            it.setLocalPath(file.toString());
            it.setMetadataJson(mapper.writeValueAsString(new MailboxExportMetadata(thread, email)));

            repository.save(it);
        }
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

        String filename = sanitizeFilename(doc.name()) + extractExtension(doc.file());
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
        // Replace characters forbidden on Windows/Linux filesystems
        String s = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_");
        s = s.replaceAll("_+", "_").strip();
        // Remove leading/trailing dots (hidden files on Linux, invalid on Windows)
        s = s.replaceAll("^[.]+|[.]+$", "");
        return s.isEmpty() ? "unnamed" : s;
    }

    private static String extractExtension(Document.DocumentFile file) {
        if (file == null || file.url() == null) {
            return "";
        }
        // Strip query string and grab the last path segment
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

    /**
     * Return set of exported mailbox thread ids (extracted from materaId pattern).
     */
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
