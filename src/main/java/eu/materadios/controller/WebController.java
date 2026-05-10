package eu.materadios.controller;

import eu.materadios.model.ExportedItem;
import eu.materadios.service.ExportService;
import eu.materadios.service.MateraApiService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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
        List<ExportedItem> items = exportService.listAll();
        model.addAttribute("items", items);
        return "index";
    }

    @GetMapping("/matera/buildings")
    public String materaBuildings(Model model) {
        try {
            List<Map<String, Object>> buildings = materaApiService.getBuildings();
            model.addAttribute("buildings", buildings);
        } catch (Exception ex) {
            model.addAttribute("buildingsError", ex.getMessage());
            model.addAttribute("buildings", List.of());
        }
        return "buildings";
    }

    @GetMapping("/matera/debug-env")
    public String materaDebugEnv(Model model) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        // system properties and env
        map.put("system.matera.username", System.getProperty("matera.username") == null ? "<null>" : "present");
        map.put("system.matera.password", System.getProperty("matera.password") == null ? "<null>" : "present");
        map.put("env.MATERA_USERNAME", System.getenv("MATERA_USERNAME") == null ? "<null>" : "present");
        map.put("env.MATERA_PASSWORD", System.getenv("MATERA_PASSWORD") == null ? "<null>" : "present");
        // try simple file parse in cwd and parents
        java.nio.file.Path found = null;
        java.nio.file.Path cur = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
        java.nio.file.Path p = cur;
        while (p != null) {
            java.nio.file.Path cand = p.resolve(".env");
            if (java.nio.file.Files.exists(cand)) { found = cand; break; }
            p = p.getParent();
        }
        if (found != null) {
            map.put("file.path", found.toString());
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(found);
                for (String ln : lines) {
                    String l = ln.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    int eq = l.indexOf('=');
                    if (eq <= 0) continue;
                    String k = l.substring(0, eq).trim();
                    String v = l.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if (k.startsWith("MATERA_")) map.put("file." + k, v.length()>2? v.substring(0,1)+"***"+v.substring(v.length()-1):"***");
                }
            } catch (Exception e) {
                map.put("file.error", e.getMessage());
            }
        } else {
            map.put("file.path", "<not found>");
        }
        model.addAttribute("probe", map);
        return "debug-env";
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

    @PostMapping("/export/batch")
    public String exportBatch(@RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                              @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                              RedirectAttributes ra) {
        try {
            Instant s = start.toInstant(ZoneOffset.UTC);
            Instant e = end.toInstant(ZoneOffset.UTC);
            List<ExportedItem> items = exportService.listByRange(s, e);
            // TODO: kick background batch export for selected items
            ra.addFlashAttribute("message", "Scheduled batch export for " + items.size() + " items. TODO: implement batch processing.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Batch export failed: " + ex.getMessage());
        }
        return "redirect:/";
    }
}
