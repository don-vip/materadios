package eu.materadios.service;

import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import eu.materadios.api.Document;
import eu.materadios.api.DocumentFolder;
import eu.materadios.api.DocumentsResponse;
import java.util.Optional;

import eu.materadios.model.ExportedItem;
import eu.materadios.repository.ExportedItemRepository;

@Service
public class BulkDocumentExportService {

    private static final Logger log = LoggerFactory.getLogger(BulkDocumentExportService.class);

    public record Status(
            boolean running,
            String phase,
            long foldersProcessed,
            long documentsProcessed,
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
    private final ExportedItemRepository repository;

    public BulkDocumentExportService(MateraApiService materaApiService, ExportService exportService,
            ExportedItemRepository repository) {
        this.materaApiService = materaApiService;
        this.exportService = exportService;
        this.repository = repository;
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
        Thread.ofVirtual().name("bulk-document-export").start(this::run);
        return true;
    }

    public void cancel() {
        cancelRequested.set(true);
    }

    private void run() {
        long foldersProcessed = 0;
        long documentsProcessed = 0;
        long errorsCount = 0;
        String lastError = null;
        Instant startedAt = currentStatus.get().startedAt();

        try {
            // BFS traversal of the Matera document folder tree starting from root
            // LinkedList is used instead of ArrayDeque because it supports null (root folder = null)
            Deque<Long> folderQueue = new LinkedList<>();
            folderQueue.add(null); // null = root

            while (!folderQueue.isEmpty()) {
                if (cancelRequested.get()) break;

                Long folderId = folderQueue.poll();

                // Enqueue sub-folders
                List<DocumentFolder> subFolders = materaApiService.getDocumentFolders(folderId);
                for (DocumentFolder sub : subFolders) {
                    folderQueue.add(sub.id());
                }

                // Process all documents in this folder (paginated)
                String after = null;
                do {
                    if (cancelRequested.get()) break;

                    DocumentsResponse page = materaApiService.getDocuments(folderId, after);
                    for (Document doc : page.results()) {
                        if (cancelRequested.get()) break;

                        try {
                            String materaId = "document:" + doc.id();

                            // Step 1: download to disk if not already there
                            if (!repository.existsByMateraId(materaId)) {
                                exportService.saveDocumentExport(doc);
                            }

                            // Step 2: upload to Drive if not already exported
                            Optional<ExportedItem> item = repository.findByMateraId(materaId);
                            if (item.isPresent() && !Boolean.TRUE.equals(item.get().getExported())) {
                                exportService.exportItemToGoogle(item.get().getId());
                            }

                            documentsProcessed++;
                        } catch (Exception ex) {
                            errorsCount++;
                            lastError = "Document " + doc.id() + ": " + ex.getMessage();
                            log.error("Bulk document export: failed document {}", doc.id(), ex);
                            documentsProcessed++;
                        }

                        currentStatus.set(new Status(true, "running", foldersProcessed,
                                documentsProcessed, errorsCount, lastError, startedAt, null));
                    }
                    after = page.meta().has_next_page() ? page.meta().end_cursor() : null;
                } while (after != null);

                foldersProcessed++;
                currentStatus.set(new Status(true, "running", foldersProcessed,
                        documentsProcessed, errorsCount, lastError, startedAt, null));
            }
        } catch (Exception ex) {
            log.error("Bulk document export: fatal error", ex);
            currentStatus.set(new Status(false, "failed", foldersProcessed, documentsProcessed,
                    errorsCount + 1, ex.getMessage(), startedAt, Instant.now()));
            running.set(false);
            return;
        }

        String finalPhase = cancelRequested.get() ? "cancelled" : "done";
        currentStatus.set(new Status(false, finalPhase, foldersProcessed, documentsProcessed,
                errorsCount, lastError, startedAt, Instant.now()));
        running.set(false);
    }
}
