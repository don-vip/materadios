package eu.materadios.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import eu.materadios.api.Building;
import eu.materadios.api.BuildingCharac;
import eu.materadios.api.BuildingConfig;
import eu.materadios.api.DocumentFolder;
import eu.materadios.api.DocumentsResponse;
import eu.materadios.api.ElectronicLettersResponse;
import eu.materadios.api.LettersResponse;
import eu.materadios.api.MailboxInfo;
import eu.materadios.api.MailboxThread;
import eu.materadios.api.MailboxThreadsResponse;
import eu.materadios.api.PrivateTopicsResponse;
import eu.materadios.api.Project;
import eu.materadios.api.TopicsResponse;
import eu.materadios.model.ExportedItem;
import eu.materadios.model.ProjectLabel;
import eu.materadios.repository.ProjectLabelRepository;
import eu.materadios.service.AutoExportService;
import eu.materadios.service.BulkDocumentExportService;
import eu.materadios.service.BulkEmailExportService;
import eu.materadios.service.ExportService;
import eu.materadios.service.GoogleService;
import eu.materadios.service.MateraApiService;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    public record ThreadGroup(long threadId, String subject, int emailCount, boolean allExported) {}

    private final ExportService exportService;
    private final MateraApiService materaApiService;
    private final GoogleService googleService;
    private final ProjectLabelRepository projectLabelRepository;
    private final BulkEmailExportService bulkEmailExportService;
    private final BulkDocumentExportService bulkDocumentExportService;
    private final AutoExportService autoExportService;

    public WebController(ExportService exportService, MateraApiService materaApiService,
            GoogleService googleService, ProjectLabelRepository projectLabelRepository,
            BulkEmailExportService bulkEmailExportService,
            BulkDocumentExportService bulkDocumentExportService,
            AutoExportService autoExportService) {
        this.exportService = exportService;
        this.materaApiService = materaApiService;
        this.googleService = googleService;
        this.projectLabelRepository = projectLabelRepository;
        this.bulkEmailExportService = bulkEmailExportService;
        this.bulkDocumentExportService = bulkDocumentExportService;
        this.autoExportService = autoExportService;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(value = "tab", required = false, defaultValue = "email") String tab,
            @RequestParam(value = "filter", required = false, defaultValue = "pending") String filter,
            Model model) {
        List<eu.materadios.model.ExportedItem> all = exportService.listAll();

        // Email threads tab
        Map<Long, List<eu.materadios.model.ExportedItem>> byThread = new LinkedHashMap<>();
        for (eu.materadios.model.ExportedItem e : all) {
            if ("EMAIL".equals(e.getType())) {
                long tid = Long.parseLong(e.getMateraId().split(":")[1]);
                byThread.computeIfAbsent(tid, _ -> new ArrayList<>()).add(e);
            }
        }
        List<ThreadGroup> threadGroups = byThread.entrySet().stream()
                .map(entry -> {
                    long tid = entry.getKey();
                    List<eu.materadios.model.ExportedItem> items = entry.getValue();
                    boolean allExp = items.stream().allMatch(e -> Boolean.TRUE.equals(e.getExported()));
                    String subject = items.stream().map(WebController::extractSubject)
                            .filter(s -> !"(no subject)".equals(s)).findFirst().orElse("(no subject)");
                    return new ThreadGroup(tid, subject, items.size(), allExp);
                })
                .filter(tg -> "exported".equals(filter) ? tg.allExported() : !tg.allExported())
                .toList();

        // Documents tab
        List<eu.materadios.model.ExportedItem> documents = all.stream()
                .filter(e -> "DOCUMENT".equals(e.getType()))
                .filter(e -> "exported".equals(filter)
                        ? Boolean.TRUE.equals(e.getExported()) : !Boolean.TRUE.equals(e.getExported()))
                .toList();

        // Attachments tab (informational, no export button)
        List<eu.materadios.model.ExportedItem> attachments = all.stream()
                .filter(e -> "EMAIL_ATTACHMENT".equals(e.getType())).toList();

        long emailTotal = all.stream().filter(e -> "EMAIL".equals(e.getType())).count();
        long docTotal = all.stream().filter(e -> "DOCUMENT".equals(e.getType())).count();
        long attTotal = all.stream().filter(e -> "EMAIL_ATTACHMENT".equals(e.getType())).count();

        String exportedRoot = Paths.get("data", "exported").toAbsolutePath().toString()
                + java.io.File.separator;

        model.addAttribute("tab", tab);
        model.addAttribute("filter", filter);
        model.addAttribute("threadGroups", threadGroups);
        model.addAttribute("documents", documents);
        model.addAttribute("attachments", attachments);
        model.addAttribute("emailTotal", emailTotal);
        model.addAttribute("docTotal", docTotal);
        model.addAttribute("attTotal", attTotal);
        model.addAttribute("exportedRoot", exportedRoot);
        model.addAttribute("bulkStatus", bulkEmailExportService.getStatus());
        model.addAttribute("bulkDocStatus", bulkDocumentExportService.getStatus());
        model.addAttribute("autoStatus", autoExportService.getStatus());
        return "index";
    }

    @PostMapping("/auto-export/enable")
    public String enableAutoExport(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        autoExportService.enable();
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @PostMapping("/auto-export/disable")
    public String disableAutoExport(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        autoExportService.disable();
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @GetMapping(value = "/auto-export/status", produces = "application/json")
    @ResponseBody
    public AutoExportService.Status autoExportStatus() {
        return autoExportService.getStatus();
    }

    private static String stackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String extractSubject(eu.materadios.model.ExportedItem item) {
        String json = item.getMetadataJson();
        if (json == null) return "(no subject)";
        int idx = json.indexOf("\"subject\":\"");
        if (idx < 0) return "(no subject)";
        int start = idx + 11;
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "(no subject)";
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

    @GetMapping("/matera/mails")
    public String mails(@RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "threadId", required = false) Long threadId, Model model) {
        if (tab == null || tab.isBlank()) tab = "inbox";
        if (after != null && after.isBlank()) after = null;
        model.addAttribute("tab", tab);
        try {
            MailboxInfo info = materaApiService.getMailboxInfo();
            model.addAttribute("mailboxEmail", info.mailbox_email_address());
            MailboxThreadsResponse resp = materaApiService.getMailboxThreads(tab, after);
            model.addAttribute("threads", deduplicateThreads(resp.results()));
            model.addAttribute("meta", resp.meta());
            model.addAttribute("exportedThreads", exportService.exportedMailboxThreadIds());
            if (threadId != null) {
                MailboxThread thread = materaApiService.getMailboxThread(threadId);
                model.addAttribute("selectedThread", thread);
                model.addAttribute("threadId", threadId);
            }
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "mails";
    }

    private static List<MailboxThread> deduplicateThreads(List<MailboxThread> threads) {
        Set<Long> seen = new HashSet<>();
        List<MailboxThread> result = new ArrayList<>();
        for (MailboxThread t : threads) {
            if (seen.add(t.id())) {
                result.add(t);
            }
        }
        return result;
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

    public record ProjectWithLabel(Project project, ProjectLabel label) {}

    @GetMapping("/matera/projects")
    public String projects(Model model) {
        try {
            List<Project> projects = materaApiService.getProjects();
            List<ProjectWithLabel> withLabels = projects.stream()
                    .map(p -> new ProjectWithLabel(p, projectLabelRepository.findById(p.id()).orElse(null)))
                    .toList();
            model.addAttribute("projects", withLabels);
            model.addAttribute("parentLabel", googleService.getProjectsParentLabel());
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return "projects";
    }

    @PostMapping("/sync/gmail/labels/project/{id}")
    public String syncProjectLabel(@PathVariable long id, RedirectAttributes ra) {
        try {
            List<Project> projects = materaApiService.getProjects();
            Project project = projects.stream().filter(p -> p.id() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
            String labelId = googleService.ensureProjectLabel(project.title());
            ProjectLabel pl = projectLabelRepository.findById(id).orElse(new ProjectLabel());
            pl.setProjectId(id);
            pl.setProjectTitle(project.title());
            pl.setGmailLabelId(labelId);
            pl.setGmailLabelName(googleService.buildProjectLabelName(project.title()));
            pl.setSyncedAt(java.time.Instant.now());
            projectLabelRepository.save(pl);
            ra.addFlashAttribute("message", "Label Gmail créé/vérifié pour : " + project.title());
        } catch (Exception ex) {
            log.error("syncProjectLabel {} failed", id, ex);
            ra.addFlashAttribute("error", "Sync failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/matera/projects";
    }

    @PostMapping("/sync/gmail/labels/projects")
    public String syncAllProjectLabels(RedirectAttributes ra) {
        try {
            List<Project> projects = materaApiService.getProjects();
            // Fetch existing Gmail labels once to avoid N API calls
            var existingLabels = googleService.listGmailLabels();
            int created = 0;
            int existing = 0;
            for (Project project : projects) {
                String labelName = googleService.buildProjectLabelName(project.title());
                String labelId = existingLabels.containsKey(labelName)
                        ? existingLabels.get(labelName)
                        : googleService.createGmailLabel(labelName);
                boolean isNew = !existingLabels.containsKey(labelName);
                ProjectLabel pl = projectLabelRepository.findById(project.id()).orElse(new ProjectLabel());
                pl.setProjectId(project.id());
                pl.setProjectTitle(project.title());
                pl.setGmailLabelId(labelId);
                pl.setGmailLabelName(labelName);
                pl.setSyncedAt(java.time.Instant.now());
                projectLabelRepository.save(pl);
                if (isNew) created++; else existing++;
            }
            ra.addFlashAttribute("message",
                    created + " label(s) créé(s), " + existing + " déjà existant(s).");
        } catch (Exception ex) {
            log.error("syncAllProjectLabels failed", ex);
            ra.addFlashAttribute("error", "Sync failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/matera/projects";
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

    @PostMapping("/export/all/documents")
    public String exportAllDocuments(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        boolean started = bulkDocumentExportService.start();
        if (!started) {
            ra.addFlashAttribute("error", "A document export job is already running.");
        }
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @PostMapping("/export/all/documents/cancel")
    public String cancelExportAllDocuments(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        bulkDocumentExportService.cancel();
        ra.addFlashAttribute("message", "Cancellation requested — the job will stop after the current document.");
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @GetMapping(value = "/export/all/documents/status", produces = "application/json")
    @ResponseBody
    public BulkDocumentExportService.Status exportAllDocumentsStatus() {
        return bulkDocumentExportService.getStatus();
    }

    @PostMapping("/export/all/emails")
    public String exportAllEmails(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        boolean started = bulkEmailExportService.start();
        if (!started) {
            ra.addFlashAttribute("error", "An export job is already running.");
        }
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @PostMapping("/export/all/emails/cancel")
    public String cancelExportAllEmails(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        bulkEmailExportService.cancel();
        ra.addFlashAttribute("message", "Cancellation requested — the job will stop after the current thread.");
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @GetMapping(value = "/export/all/emails/status", produces = "application/json")
    @ResponseBody
    public BulkEmailExportService.Status exportAllEmailsStatus() {
        return bulkEmailExportService.getStatus();
    }

    @PostMapping("/export/item/{id}")
    public String exportItem(@PathVariable Long id,
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.exportItemToGoogle(id);
            ra.addFlashAttribute("message", "Started export for item " + id + ".");
        } catch (Exception ex) {
            log.error("exportItem {} failed", id, ex);
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @PostMapping("/admin/reset/document/{id}")
    public String resetDocumentExported(@PathVariable Long id,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.resetDocumentExported(id);
            ra.addFlashAttribute("message", "Document " + id + " reset — ready to re-export to Drive.");
        } catch (Exception ex) {
            log.error("resetDocumentExported {} failed", id, ex);
            ra.addFlashAttribute("error", "Reset failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=document&filter=" + filter;
    }

    @PostMapping("/admin/reset/thread/{threadId}")
    public String resetThreadExported(@PathVariable long threadId,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.resetAndReexportThread(threadId);
            ra.addFlashAttribute("message", "Thread " + threadId + " reset and re-exported to disk — ready to import to Gmail.");
        } catch (Exception ex) {
            log.error("resetThreadExported {} failed", threadId, ex);
            ra.addFlashAttribute("error", "Reset failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=email&filter=" + filter;
    }

    @PostMapping("/export/google/thread/{threadId}")
    public String exportGoogleThread(@PathVariable long threadId,
            @RequestParam(value = "filter", required = false, defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.exportThreadToGoogle(threadId);
            ra.addFlashAttribute("message", "Thread " + threadId + " exported to Gmail.");
        } catch (Exception ex) {
            log.error("exportGoogleThread {} failed", threadId, ex);
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=email&filter=" + filter;
    }

    @PostMapping("/export/mailbox/thread/{threadId}")
    public String exportMailboxThread(@PathVariable Long threadId, RedirectAttributes ra) {
        try {
            exportService.exportMailboxThreadToDisk(threadId);
            ra.addFlashAttribute("message", "Started export for mailbox thread " + threadId + ".");
        } catch (Exception ex) {
            log.error("exportMailboxThread {} failed", threadId, ex);
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/matera/mails";
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
            ra.addFlashAttribute("message",
                    "Scheduled batch export for " + items.size() + " items. TODO: implement batch processing.");
        } catch (Exception ex) {
            log.error("exportBatch failed", ex);
            ra.addFlashAttribute("error", "Batch export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/";
    }

    @PostMapping("/admin/purge/database")
    public String purgeDatabase(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.purgeDatabase();
            ra.addFlashAttribute("message", "Local database purged.");
        } catch (Exception ex) {
            log.error("purgeDatabase failed", ex);
            ra.addFlashAttribute("error", "Database purge failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=" + tab + "&filter=" + filter;
    }

    @PostMapping("/admin/purge/files")
    public String purgeFiles(
            @RequestParam(defaultValue = "email") String tab,
            @RequestParam(defaultValue = "pending") String filter,
            RedirectAttributes ra) {
        try {
            exportService.purgeLocalFiles();
            ra.addFlashAttribute("message", "Local files purged.");
        } catch (Exception ex) {
            log.error("purgeFiles failed", ex);
            ra.addFlashAttribute("error", "File purge failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
        }
        return "redirect:/?tab=" + tab + "&filter=" + filter;
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
            log.error("exportDocument {} failed", id, ex);
            ra.addFlashAttribute("error", "Export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
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
            log.error("exportDocumentFolder {} failed", folderId, ex);
            ra.addFlashAttribute("error", "Folder export failed: " + ex.getMessage());
            ra.addFlashAttribute("errorDetail", stackTrace(ex));
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
