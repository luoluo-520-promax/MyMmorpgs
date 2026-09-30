package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "update-service", contextId = "updateCommandClient", configuration = InternalApiFeignConfiguration.class)
public interface UpdateCommandClient {

    @PostMapping(value = "/internal/update/manifest",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] manifest(@RequestBody byte[] body);

    @PostMapping(value = "/internal/update/verify",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] verify(@RequestBody byte[] body);
}
