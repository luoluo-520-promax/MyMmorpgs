package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "shop-service", contextId = "shopImportClient", configuration = AdminFeignConfiguration.class)
public interface ShopImportClient {

    @PostMapping(value = "/internal/shop/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> importJson(@RequestParam("dryRun") boolean dryRun, @RequestBody String body);

    @PostMapping(value = "/internal/shop/import", consumes = "text/csv")
    Map<String, Object> importCsv(@RequestParam("dryRun") boolean dryRun, @RequestBody String body);

    @PostMapping("/internal/shop/reload")
    Map<String, Object> reload();
}
