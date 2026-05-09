package eu.materadios.controller;

import eu.materadios.model.ExportedItem;
import eu.materadios.service.ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Controller
public class WebController {

    private final ExportService exportService;

    public WebController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ExportedItem> items = exportService.listAll();
        model.addAttribute("items", items);
        return "index";
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
