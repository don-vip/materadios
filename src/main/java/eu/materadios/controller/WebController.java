package eu.materadios.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import eu.materadios.api.Building;
import eu.materadios.api.BuildingCharac;
import eu.materadios.api.BuildingConfig;
import eu.materadios.api.Document;
import eu.materadios.api.DocumentFolder;
import eu.materadios.api.DocumentsResponse;
import eu.materadios.api.ElectronicLettersResponse;
import eu.materadios.api.LettersResponse;
import eu.materadios.api.MailboxThreadsResponse;
import eu.materadios.api.PrivateTopicsResponse;
import eu.materadios.api.TopicsResponse;
import eu.materadios.model.ExportedItem;
import eu.materadios.service.ExportService;
import eu.materadios.service.MateraApiService;

@Controller
public class WebController {

    private final ExportService exportService;

    private final MateraApiService materaApiService;

    public WebController(ExportService exportService, MateraApiService materaApiService) {
        this.exportService = exportService;
        this.materaApiService = materaApiService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("items", exportService.listAll());
        return "index";
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> serveLocalFile(@PathVariable Long id) throws IOException {
        var item = exportService.findById(id);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        Path filePath = Paths.get(item.getLocalPath());
        // Restrict to the data/exported directory to prevent path traversal
        Path exportRoot = Paths.get("data", "exported").toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(exportRoot)) {
            return ResponseEntity.status(403).build();
        }
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = Files.readAllBytes(filePath);
        String mediaType = Files.probeContentType(filePath);
        if (mediaType == null) {
            mediaType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(mediaType))
                .body(content);
    }

    @GetMapping("/matera/context")
    public String materaContext(Model model) {
        try {
            model.addAttribute("context", materaApiService.getContext());
        } catch (Exception ex) {
            model.addAttribute("contextError", ex.getMessage());
        }
        return "context";
    }

    // New UI pages for Matera resources

    @GetMapping("/matera/building")
    public String building(Model model) {
        try {
            var ctx = materaApiService.getContext();
            long buildingId = ctx.building().id();
            Building b = materaApiService.getBuilding(buildingId);
            BuildingCharac c = materaApiService.getBuildingCharac(buildingId);
            BuildingConfig cfg = materaApiService.getBuildingConfig(buildingId);
            model.addAttribute("building", b);
            model.addAttribute("charac", c);
            model.addAttribute("config", cfg);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "building";
    }

    @GetMapping("/matera/exercices")
    public String exercices(Model model) {
        try {
            var list = materaApiService.getExercices();
            model.addAttribute("exercices", list);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "exercices";
    }

    @GetMapping("/matera/letters")
    public String letters(@RequestParam(value = "after", required = false) String after, Model model) {
        try {
            LettersResponse resp = materaApiService.getLetters(after);
            model.addAttribute("letters", resp.results());
            model.addAttribute("meta", resp.meta());
            model.addAttribute("after", after);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "letters";
    }

    @GetMapping("/matera/electronic_letters")
    public String electronicLetters(@RequestParam(value = "after", required = false) String after, Model model) {
        try {
            ElectronicLettersResponse resp = materaApiService.getElectronicLetters(after);
            model.addAttribute("letters", resp.results());
            model.addAttribute("meta", resp.meta());
            model.addAttribute("after", after);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "electronic_letters";
    }

    @GetMapping("/matera/mailbox/info")
    public String mailboxInfo(Model model) {
        try {
            model.addAttribute("info", materaApiService.getMailboxInfo());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "mailbox_info";
    }

    @GetMapping("/matera/mailbox/threads")
    public String mailboxThreads(@RequestParam(value = "after", required = false) String after, Model model) {
        try {
            MailboxThreadsResponse resp = materaApiService.getMailboxThreads(after);
            model.addAttribute("threads", resp.results());
            model.addAttribute("meta", resp.meta());
            model.addAttribute("after", after);
            model.addAttribute("exportedThreads", exportService.exportedMailboxThreadIds());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "mailbox_threads";
    }

    @GetMapping("/matera/meters")
    public String meters(Model model) {
        try {
            model.addAttribute("meters", materaApiService.getMeters());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "meters";
    }

    @GetMapping("/matera/mutations")
    public String mutations(Model model) {
        try {
            model.addAttribute("mutations", materaApiService.getMutations());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "mutations";
    }

    @GetMapping("/matera/owners")
    public String owners(Model model) {
        try {
            model.addAttribute("owners", materaApiService.getOwners());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "owners";
    }

    @GetMapping("/matera/private_topics")
    public String privateTopics(@RequestParam(value = "after", required = false) String after, Model model) {
        try {
            PrivateTopicsResponse resp = materaApiService.getPrivateTopics(after);
            model.addAttribute("topics", resp.results());
            model.addAttribute("meta", resp.meta());
            model.addAttribute("after", after);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "private_topics";
    }

    @GetMapping("/matera/projects")
    public String projects(Model model) {
        try {
            model.addAttribute("projects", materaApiService.getProjects());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "projects";
    }

    @GetMapping("/matera/tenants")
    public String tenants(Model model) {
        try {
            model.addAttribute("tenants", materaApiService.getTenants());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "tenants";
    }

    @GetMapping("/matera/topics")
    public String topics(@RequestParam(value = "after", required = false) String after, Model model) {
        try {
            TopicsResponse resp = materaApiService.getTopics(after);
            model.addAttribute("topics", resp.results());
            model.addAttribute("meta", resp.meta());
            model.addAttribute("after", after);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "topics";
    }

    @PostMapping("/export/all")
    public String exportAll(RedirectAttributes ra) {
        try {
            exportService.exportAllFromMatera();
            ra.addFlashAttribute("message", "Started export job (see logs). TODO: implement progress reporting.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Export failed to start: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/export/item/{id}")
    public String exportItem(@PathVariable Long id, RedirectAttributes ra) {
        try {
            exportService.exportItemToGoogle(id);
            ra.addFlashAttribute("message", "Started export for item " + id + ".");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/export/mailbox/thread/{threadId}")
    public String exportMailboxThread(@PathVariable Long threadId, RedirectAttributes ra) {
        try {
            exportService.exportMailboxThreadToDisk(threadId);
            ra.addFlashAttribute("message", "Started export for mailbox thread " + threadId + ".");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
        }
        return "redirect:/matera/mailbox/threads";
    }

    @PostMapping("/export/batch")
    public String exportBatch(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            RedirectAttributes ra) {
        try {
            Instant s = start.toInstant(ZoneOffset.UTC);
            Instant e = end.toInstant(ZoneOffset.UTC);
            List<ExportedItem> items = exportService.listByRange(s, e);
            // TODO: kick background batch export for selected items
            ra.addFlashAttribute("message",
                    "Scheduled batch export for " + items.size() + " items. TODO: implement batch processing.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Batch export failed: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/admin/purge/database")
    public String purgeDatabase(RedirectAttributes ra) {
        try {
            exportService.purgeDatabase();
            ra.addFlashAttribute("message", "Local database purged.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Database purge failed: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/admin/purge/files")
    public String purgeFiles(RedirectAttributes ra) {
        try {
            exportService.purgeLocalFiles();
            ra.addFlashAttribute("message", "Local files purged.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "File purge failed: " + ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/matera/documents")
    public String documents(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "folderName", required = false) String folderName,
            @RequestParam(value = "after", required = false) String after,
            Model model) {
        try {
            List<DocumentFolder> subFolders = materaApiService.getDocumentFolders(folderId);
            DocumentsResponse docsResp = materaApiService.getDocuments(folderId, after);
            model.addAttribute("subFolders", subFolders);
            model.addAttribute("documents", docsResp.results());
            model.addAttribute("meta", docsResp.meta());
            model.addAttribute("exportedDocumentIds", exportService.exportedDocumentIds());
            model.addAttribute("folderId", folderId);
            model.addAttribute("folderName", folderName);
            model.addAttribute("after", after);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "documents";
    }

    @PostMapping("/export/document/{id}")
    public String exportDocument(@PathVariable long id,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "folderName", required = false) String folderName,
            RedirectAttributes ra) {
        try {
            exportService.exportDocumentToDisk(id);
            ra.addFlashAttribute("message", "Document exported.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
        }
        return buildDocumentsRedirect(folderId, folderName);
    }

    @PostMapping("/export/document/folder/{folderId}")
    public String exportDocumentFolder(@PathVariable long folderId,
            @RequestParam(value = "folderName", required = false) String folderName,
            RedirectAttributes ra) {
        try {
            exportService.exportDocumentFolderToDisk(folderId);
            ra.addFlashAttribute("message", "Folder exported recursively.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Folder export failed: " + ex.getMessage());
        }
        return buildDocumentsRedirect(folderId, folderName);
    }

    private static String buildDocumentsRedirect(Long folderId, String folderName) {
        if (folderId == null) {
            return "redirect:/matera/documents";
        }
        StringBuilder sb = new StringBuilder("redirect:/matera/documents?folderId=").append(folderId);
        if (folderName != null && !folderName.isBlank()) {
            sb.append("&folderName=").append(java.net.URLEncoder.encode(folderName,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @GetMapping("/matera/suppliers")
    public String suppliers(Model model) {
        try {
            model.addAttribute("suppliers", materaApiService.getSuppliers());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "suppliers";
    }
}
