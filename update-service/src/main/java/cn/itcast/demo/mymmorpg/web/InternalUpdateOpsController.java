package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.repository.ClientVersionReleaseRepository;
import cn.itcast.demo.mymmorpg.service.PatchLifecycleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 更新域热更探测：确认当前 active 版本清单可读。
 */
@RestController
@RequestMapping("/internal/ops")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service")
public class InternalUpdateOpsController {

    private final ClientVersionReleaseRepository releaseRepository;
    private final PatchLifecycleService patchLifecycleService;

    public InternalUpdateOpsController(
            ClientVersionReleaseRepository releaseRepository,
            PatchLifecycleService patchLifecycleService) {
        this.releaseRepository = releaseRepository;
        this.patchLifecycleService = patchLifecycleService;
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        long activeCount = releaseRepository.findFirstByActiveTrueOrderByVersionNumberDesc().isPresent() ? 1L : 0L;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("service", "update-service");
        body.put("activeManifestPresent", activeCount > 0);
        body.put("message", "version manifest reload probe ok");
        return body;
    }

    @PostMapping("/archive-old-patches")
    public Map<String, Object> archiveOldPatches() {
        int archived = patchLifecycleService.archiveOlderThanKeepWindow();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("archived", archived);
        return body;
    }
}
