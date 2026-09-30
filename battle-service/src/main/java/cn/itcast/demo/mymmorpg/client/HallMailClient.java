package cn.itcast.demo.mymmorpg.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "hall-service", contextId = "battleHallMailClient",
        url = "${HALL_SERVICE_URL:http://127.0.0.1:8986}")
public interface HallMailClient {

    @PostMapping(value = "/internal/hall/mails/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> sendMail(@RequestBody Map<String, Object> body);
}
