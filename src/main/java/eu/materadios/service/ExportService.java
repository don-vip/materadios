package eu.materadios.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.materadios.model.ExportedItem;
import eu.materadios.repository.ExportedItemRepository;

@Service
public class ExportService {

    private final ExportedItemRepository repository;
    private final GoogleService googleService;

    public ExportService(ExportedItemRepository repository, GoogleService googleService) {
        this.repository = repository;
        this.googleService = googleService;
    }

    /**
     * TODO: Implement Matera API client to fetch all items and persist local files.
     */
    @Transactional
    public void exportAllFromMatera() {
        // TODO: implement Matera API integration using MATERA_USERNAME / MATERA_PASSWORD env vars
        throw new UnsupportedOperationException("exportAllFromMatera is not implemented yet");
    }

    @Transactional
    public ExportedItem exportItemToGoogle(Long id) {
        Optional<ExportedItem> item = repository.findById(id);
        if (item.isEmpty()) throw new IllegalArgumentException("Item not found");

        ExportedItem e = item.get();
        if (Boolean.TRUE.equals(e.getExported())) return e; // already exported

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

}
