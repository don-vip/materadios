package eu.materadios.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
        List<ExportedItem> items = exportService.listAll();
        model.addAttribute("items", items);
        return "index";
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
