package eu.materadios.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import eu.materadios.api.MailboxThread;
import eu.materadios.api.MailboxThreadsResponse;

@Service
public class AutoExportService {

    private static final Logger log = LoggerFactory.getLogger(AutoExportService.class);

    private static final int CHECK_INTERVAL_SECONDS = 60;
    private static final String[] WATCHED_TABS = {"inbox", "sent"};
    private static final int MAX_RECENT_EVENTS = 20;

    public record ExportEvent(Instant at, long threadId, String subject, int emailCount) {}

    public record Status(
            boolean enabled,
            String phase,        // idle | checking | exporting
            Instant lastCheck,
            Instant lastExport,
            List<ExportEvent> recentEvents) {}

    private static final Status DISABLED = new Status(false, "idle", null, null, List.of());

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicReference<Status> currentStatus = new AtomicReference<>(DISABLED);
    private final ArrayDeque<ExportEvent> recentEvents = new ArrayDeque<>();

    private final MateraApiService materaApiService;
    private final ExportService exportService;

    public AutoExportService(MateraApiService materaApiService, ExportService exportService) {
        this.materaApiService = materaApiService;
        this.exportService = exportService;
    }

    public Status getStatus() {
        return currentStatus.get();
    }

    public synchronized boolean enable() {
        if (enabled.get()) return false;
        enabled.set(true);
        updateStatus("idle");
        Thread.ofVirtual().name("auto-export-loop").start(this::loop);
        return true;
    }

    public void disable() {
        enabled.set(false);
        // Status is updated by the loop when it exits; update immediately for responsiveness
        Status s = currentStatus.get();
        currentStatus.set(new Status(false, "idle", s.lastCheck(), s.lastExport(), s.recentEvents()));
    }

    private void loop() {
        while (enabled.get()) {
            try {
                check();
            } catch (Exception ex) {
                log.error("Auto-export check failed", ex);
                updateStatus("idle");
            }
            // Sleep CHECK_INTERVAL_SECONDS, waking every second to respect disable()
            for (int i = 0; i < CHECK_INTERVAL_SECONDS && enabled.get(); i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Status s = currentStatus.get();
        currentStatus.set(new Status(false, "idle", s.lastCheck(), s.lastExport(), s.recentEvents()));
    }

    private void check() {
        setPhase("checking");

        // threadId → list of email IDs from preview that are not yet on disk
        List<long[]> toExport = new ArrayList<>(); // [threadId, newEmailCount]
        Set<Long> seen = new HashSet<>();

        for (String tab : WATCHED_TABS) {
            try {
                MailboxThreadsResponse page = materaApiService.getMailboxThreads(tab, null);
                for (MailboxThread preview : page.results()) {
                    if (!seen.add(preview.id())) continue;
                    long threadId = preview.id();

                    if (!exportService.isThreadOnDisk(threadId)) {
                        // Brand new thread
                        toExport.add(new long[]{threadId, 0});
                    } else if (preview.emails() != null) {
                        // Existing thread — check if preview contains email IDs not yet on disk
                        long newInPreview = preview.emails().stream()
                                .filter(e -> !exportService.isEmailOnDisk(threadId, e.id()))
                                .count();
                        if (newInPreview > 0) {
                            toExport.add(new long[]{threadId, newInPreview});
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Auto-export: could not fetch tab '{}': {}", tab, ex.getMessage());
            }
        }

        Instant now = Instant.now();
        if (toExport.isEmpty()) {
            Status s = currentStatus.get();
            currentStatus.set(new Status(true, "idle", now, s.lastExport(), s.recentEvents()));
            return;
        }

        setPhase("exporting");
        log.info("Auto-export: {} thread(s) have new emails", toExport.size());

        List<ExportEvent> newEvents = new ArrayList<>();
        for (long[] entry : toExport) {
            if (!enabled.get()) break;
            long threadId = entry[0];
            try {
                MailboxThread full = materaApiService.getMailboxThread(threadId);
                // Only process emails not yet on disk — preserves Gmail state of existing emails
                exportService.exportMailboxThreadOnlyNew(full);
                // Idempotent: only imports emails with exported=false
                exportService.exportThreadToGoogle(threadId);
                // Count newly added emails (those just written to disk)
                int newCount = full.emails() == null ? 0 : (int) full.emails().stream()
                        .filter(e -> !exportService.isEmailOnDisk(full.id(), e.id()))
                        .count();
                // isEmailOnDisk is called after export, so newCount ≈ 0 now; use preview count instead
                int count = (int) Math.max(entry[1], 1);
                String subject = full.emails() != null && !full.emails().isEmpty()
                        ? full.emails().get(0).subject() : "(no subject)";
                newEvents.add(new ExportEvent(Instant.now(), threadId, subject, count));
                log.info("Auto-export: exported {} new email(s) in thread {}", count, threadId);
            } catch (Exception ex) {
                log.error("Auto-export: failed thread {}", threadId, ex);
            }
        }

        synchronized (recentEvents) {
            for (int i = newEvents.size() - 1; i >= 0; i--) {
                recentEvents.addFirst(newEvents.get(i));
            }
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeLast();
            }
        }

        Instant lastExport = newEvents.isEmpty() ? currentStatus.get().lastExport() : Instant.now();
        currentStatus.set(new Status(enabled.get(), "idle", now, lastExport, List.copyOf(recentEvents)));
    }

    private void setPhase(String phase) {
        Status s = currentStatus.get();
        currentStatus.set(new Status(true, phase, s.lastCheck(), s.lastExport(), s.recentEvents()));
    }

    private void updateStatus(String phase) {
        Status s = currentStatus.get();
        currentStatus.set(new Status(enabled.get(), phase, s != null ? s.lastCheck() : null,
                s != null ? s.lastExport() : null, s != null ? s.recentEvents() : List.of()));
    }
}
