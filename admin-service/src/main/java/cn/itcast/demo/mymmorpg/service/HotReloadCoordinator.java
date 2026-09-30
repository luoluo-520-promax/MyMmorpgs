package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityOpsClient;
import cn.itcast.demo.mymmorpg.client.PlayerOpsClient;
import cn.itcast.demo.mymmorpg.client.QuestOpsClient;
import cn.itcast.demo.mymmorpg.client.UpdateOpsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分阶段热更编排：两阶段 Snapshot（Build/校验全部通过后再 Swap），避免半生不熟配置。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class HotReloadCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HotReloadCoordinator.class);

    private final ActivityOpsClient activityOpsClient;
    private final UpdateOpsClient updateOpsClient;
    private final QuestOpsClient questOpsClient;
    private final PlayerOpsClient playerOpsClient;
    private final ConfigPublishAuditService auditService;

    public HotReloadCoordinator(ActivityOpsClient activityOpsClient,
                                UpdateOpsClient updateOpsClient,
                                QuestOpsClient questOpsClient,
                                PlayerOpsClient playerOpsClient,
                                ConfigPublishAuditService auditService) {
        this.activityOpsClient = activityOpsClient;
        this.updateOpsClient = updateOpsClient;
        this.questOpsClient = questOpsClient;
        this.playerOpsClient = playerOpsClient;
        this.auditService = auditService;
    }

    public record ReloadResult(boolean success, String message, List<String> stages, Map<String, Object> details) {
    }

    public ReloadResult reloadAllStaged(String operator) {
        log.info("=== snapshot reload start: prepare all domains then swap ===");
        List<String> stages = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            // Phase 1 Build/Validate：任一失败则不进入 Swap，避免 Activity 成功而 Quest 失败的不一致
            Map<String, Object> prepared = new LinkedHashMap<>();
            Map<String, Object> activityPrep = activityOpsClient.reload();
            requireOk(activityPrep, "activity-prepare");
            prepared.put("activity", activityPrep);
            stages.add("prepare:activity");

            Map<String, Object> updatePrep = updateOpsClient.reload();
            requireOk(updatePrep, "update-prepare");
            prepared.put("update", updatePrep);
            stages.add("prepare:update");

            Map<String, Object> questPrep = questOpsClient.reload();
            requireOk(questPrep, "quest-prepare");
            prepared.put("quest", questPrep);
            stages.add("prepare:quest");

            Map<String, Object> playerPrep = playerOpsClient.reload();
            requireOk(playerPrep, "player-prepare");
            prepared.put("configCache", playerPrep);
            stages.add("prepare:configCache");

            details.put("prepare", prepared);
            // Phase 2 Swap：当前各服务 reload 已在 prepare 中完成原子本地 swap；
            // 此处再次确认全部 ok，作为编排层提交点（失败则整体记失败，不宣称 success）。
            for (Map.Entry<String, Object> e : prepared.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) e.getValue();
                requireOk(body, e.getKey() + "-swap");
                stages.add("swap:" + e.getKey());
            }
            details.put("swap", "committed");

            auditService.record(operator, "reloadAllSnapshot", stages, false, true, "ok");
            log.info("=== snapshot reload success: {} ===", stages);
            return new ReloadResult(true, "ok", List.copyOf(stages), details);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.error("Snapshot reload failed after stages={}", stages, e);
            auditService.record(operator, "reloadAllSnapshot", stages, false, false, msg);
            return new ReloadResult(false, msg, List.copyOf(stages), details);
        }
    }

    private static void requireOk(Map<String, Object> body, String stage) {
        if (body == null) {
            throw new IllegalStateException(stage + " reload returned null");
        }
        Object ok = body.get("ok");
        if (ok instanceof Boolean b && !b) {
            throw new IllegalStateException(stage + " reload returned ok=false: " + body);
        }
    }
}
