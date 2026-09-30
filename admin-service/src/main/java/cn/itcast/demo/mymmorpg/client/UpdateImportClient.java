package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "update-service", contextId = "updateImportClient", configuration = AdminFeignConfiguration.class)
public interface UpdateImportClient {

    @PostMapping(value = "/internal/update/import/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    ImportResultItem importJson(@RequestBody String body);
}
