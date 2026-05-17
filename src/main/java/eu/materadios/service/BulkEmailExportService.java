package eu.materadios.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import eu.materadios.api.MailboxThread;
import eu.materadios.api.MailboxThreadsResponse;

@Service
public class BulkEmailExportService {

    private static final Logger log = LoggerFactory.getLogger(BulkEmailExportService.class);

    private static final String[] TABS = {"inbox", "sent", "drafts", "archived", "deleted"};

    public record Status(
            boolean running,
            String phase,
            long threadsProcessed,
            long emailsProcessed,
            long errorsCount,
            String lastError,
            Instant startedAt,
            Instant finishedAt) {

        static final Status IDLE = new Status(false, "idle", 0, 0, 0, null, null, null);
    }

    private final AtomicReference<Status> currentStatus = new AtomicReference<>(Status.IDLE);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final MateraApiService materaApiService;
    private final ExportService exportService;

    public BulkEmailExportService(MateraApiService materaApiService, ExportService exportService) {
        this.materaApiService = materaApiService;
        this.exportService = exportService;
    }

    public Status getStatus() {
        return currentStatus.get();
    }

    public synchronized boolean start() {
        if (running.get()) {
            return false;
        }
        running.set(true);
        cancelRequested.set(false);
        currentStatus.set(new Status(true, "running", 0, 0, 0, null, Instant.now(), null));
        Thread.ofVirtual().name("bulk-email-export").start(this::run);
        return true;
    }

    public void cancel() {
        cancelRequested.set(true);
    }

    private void run() {
        Set<Long> seen = new HashSet<>();
        long threadsProcessed = 0;
        long emailsProcessed = 0;
        long errorsCount = 0;
        String lastError = null;
        Instant startedAt = currentStatus.get().startedAt();

        try {
            outer:
            for (String tab : TABS) {
                String after = null;
                do {
                    if (cancelRequested.get()) {
                        break outer;
                    }
                    MailboxThreadsResponse page = materaApiService.getMailboxThreads(tab, after);
                    for (MailboxThread preview : page.results()) {
                        if (cancelRequested.get()) {
                            break outer;
                        }
                        if (!seen.add(preview.id())) {
                            continue; // already processed (cross-tab duplicate)
                        }
                        try {
                            // Skip disk export if already downloaded — exportMailboxThread resets
                            // the Gmail exported flag, which would cause duplicate Gmail imports.
                            if (!exportService.isThreadOnDisk(preview.id())) {
                                MailboxThread full = materaApiService.getMailboxThread(preview.id());
                                exportService.exportMailboxThread(full);
                                emailsProcessed += full.emails() != null ? full.emails().size() : 0;
                            }
                            // Idempotent: skips emails already marked exported in the DB
                            exportService.exportThreadToGoogle(preview.id());
                            threadsProcessed++;
                        } catch (Exception ex) {
                            errorsCount++;
                            lastError = "Thread " + preview.id() + ": " + ex.getMessage();
                            log.error("Bulk export: failed thread {}", preview.id(), ex);
                            threadsProcessed++;
                        }
                        currentStatus.set(new Status(true, "running", threadsProcessed, emailsProcessed,
                                errorsCount, lastError, startedAt, null));
                    }
                    after = page.meta().has_next_page() ? page.meta().end_cursor() : null;
                } while (after != null);
            }
        } catch (Exception ex) {
            log.error("Bulk export: fatal error", ex);
            currentStatus.set(new Status(false, "failed", threadsProcessed, emailsProcessed,
                    errorsCount + 1, ex.getMessage(), startedAt, Instant.now()));
            running.set(false);
            return;
        }

        String finalPhase = cancelRequested.get() ? "cancelled" : "done";
        currentStatus.set(new Status(false, finalPhase, threadsProcessed, emailsProcessed,
                errorsCount, lastError, startedAt, Instant.now()));
        running.set(false);
    }
}
