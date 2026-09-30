package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import cn.itcast.demo.mymmorpg.service.ActivityImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/activity/import")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class InternalActivityImportController {

    private final ActivityImportService importService;

    public InternalActivityImportController(ActivityImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<ImportResultItem> importJson(@RequestBody String json) throws Exception {
        return importService.importFromJson(json).stream()
                .map(a -> ImportResultItem.activity(a.getId(), a.getType()))
                .toList();
    }

    @PostMapping(value = "/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ActivityImportDryRunResult dryRunJson(@RequestBody String json) throws Exception {
        return importService.dryRunFromJson(json);
    }

    @PostMapping(value = "/csv", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public List<ImportResultItem> importCsv(@RequestBody String csv) throws Exception {
        return importService.importFromCsv(csv).stream()
                .map(a -> ImportResultItem.activity(a.getId(), a.getType()))
                .toList();
    }
}
