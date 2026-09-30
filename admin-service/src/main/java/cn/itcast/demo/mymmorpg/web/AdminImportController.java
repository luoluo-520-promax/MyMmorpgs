package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import cn.itcast.demo.mymmorpg.service.AdminImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/import")
public class AdminImportController {

    private final AdminImportService adminImportService;

    public AdminImportController(AdminImportService adminImportService) {
        this.adminImportService = adminImportService;
    }

    @PostMapping(value = "/activities", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importActivitiesJson(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody String body) {
        List<ImportResultItem> items = adminImportService.importActivitiesFromJson(adminUserId, body);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "count", items.size(),
                "items", items));
    }

    @PostMapping(value = "/activities", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> importActivitiesCsv(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody String body) {
        List<ImportResultItem> items = adminImportService.importActivitiesFromCsv(adminUserId, body);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "count", items.size(),
                "items", items));
    }

    @PostMapping(value = "/manifests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importManifestJson(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody String body) {
        ImportResultItem item = adminImportService.importManifestFromJson(adminUserId, body);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "item", item));
    }

    @PostMapping(value = "/shop-products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importShopProducts(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) {
        Map<String, Object> item = adminImportService.importShopProductsFromJson(adminUserId, body, dryRun);
        return ResponseEntity.ok(Map.of(
                "status", Boolean.TRUE.equals(item.get("ok")) ? "OK" : "FAIL",
                "item", item));
    }

    @PostMapping(value = "/shop-products", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> importShopProductsCsv(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) {
        Map<String, Object> item = adminImportService.importShopProductsFromCsv(adminUserId, body, dryRun);
        return ResponseEntity.ok(Map.of(
                "status", Boolean.TRUE.equals(item.get("ok")) ? "OK" : "FAIL",
                "item", item));
    }

    @PostMapping(value = "/quests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> importQuests(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) {
        Map<String, Object> item = adminImportService.importQuestsFromJson(adminUserId, body, dryRun);
        return ResponseEntity.ok(Map.of(
                "status", Boolean.TRUE.equals(item.get("ok")) ? "OK" : "FAIL",
                "item", item));
    }

    @PostMapping(value = "/quests", consumes = {"text/csv", "application/csv", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> importQuestsCsv(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody String body) {
        Map<String, Object> item = adminImportService.importQuestsFromCsv(adminUserId, body, dryRun);
        return ResponseEntity.ok(Map.of(
                "status", Boolean.TRUE.equals(item.get("ok")) ? "OK" : "FAIL",
                "item", item));
    }
}
