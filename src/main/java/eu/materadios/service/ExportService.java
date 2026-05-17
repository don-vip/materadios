package eu.materadios.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.materadios.api.MailboxThread;
import eu.materadios.model.ExportedItem;
import eu.materadios.model.MailboxExportMetadata;
import eu.materadios.repository.ExportedItemRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExportService {

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
