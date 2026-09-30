package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "activity-service", contextId = "activityImportClient", configuration = AdminFeignConfiguration.class)
public interface ActivityImportClient {

    @PostMapping(value = "/internal/activity/import/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    List<ImportResultItem> importJson(@RequestBody String body);

    @PostMapping(value = "/internal/activity/import/dry-run", consumes = MediaType.APPLICATION_JSON_VALUE)
    ActivityImportDryRunResult dryRunJson(@RequestBody String body);

    @PostMapping(value = "/internal/activity/import/csv", consumes = "text/csv")
    List<ImportResultItem> importCsv(@RequestBody String body);
}
