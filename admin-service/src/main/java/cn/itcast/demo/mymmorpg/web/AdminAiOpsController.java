package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.client.BattleAiClient;
import cn.itcast.demo.mymmorpg.service.ai.AiDraftVersionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss BT 热更新代理 + AI 草稿审核流。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ai/ops")
public class AdminAiOpsController {

    private final BattleAiClient battleAiClient;
    private final AiDraftVersionStore draftVersionStore;

    public AdminAiOpsController(BattleAiClient battleAiClient, AiDraftVersionStore draftVersionStore) {
        this.battleAiClient = battleAiClient;
        this.draftVersionStore = draftVersionStore;
    }

    @PostMapping("/bt/load")
    public ResponseEntity<Map<String, Object>> loadBt(@RequestParam String treeId,
                                                      @RequestBody(required = false) List<Map<String, Object>> nodes) {
        return ResponseEntity.ok(battleAiClient.loadBehaviorTree(treeId, nodes));
    }

    @GetMapping("/bt/inspect")
    public ResponseEntity<Map<String, Object>> inspectBt(@RequestParam String treeId) {
        return ResponseEntity.ok(battleAiClient.inspectBehaviorTree(treeId));
    }

    @PostMapping("/bt/configure-boss")
    public ResponseEntity<Map<String, Object>> configureBoss(@RequestParam String botId,
                                                             @RequestBody(required = false) List<Map<String, Object>> nodes) {
        return ResponseEntity.ok(battleAiClient.configureBossTree(botId, nodes));
    }

    @PostMapping("/drafts")
    public ResponseEntity<Map<String, Object>> createDraft(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String domain = body == null ? "activity" : String.valueOf(body.getOrDefault("domain", "activity"));
        String json = body == null || body.get("documentJson") == null ? "{}" : String.valueOf(body.get("documentJson"));
        String promptVersion = body == null ? "" : String.valueOf(body.getOrDefault("promptVersion", ""));
        AiDraftVersionStore.DraftVersion v = draftVersionStore.create(adminUserId, domain, json, promptVersion);
        return ResponseEntity.ok(toView(v));
    }

    @PostMapping("/drafts/{draftId}/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable String draftId) {
        return draftVersionStore.submit(draftId, adminUserId)
                .map(v -> ResponseEntity.ok(toView(v)))
                .orElse(ResponseEntity.badRequest().body(Map.of("ok", false, "error", "invalid_transition")));
    }

    @PostMapping("/drafts/{draftId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable String draftId,
            @RequestBody(required = false) Map<String, Object> body) {
        String note = body == null || body.get("note") == null ? "" : String.valueOf(body.get("note"));
        return draftVersionStore.approve(draftId, adminUserId, note)
                .map(v -> ResponseEntity.ok(toView(v)))
                .orElse(ResponseEntity.badRequest().body(Map.of("ok", false, "error", "invalid_transition")));
    }

    @PostMapping("/drafts/{draftId}/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable String draftId) {
        return draftVersionStore.publish(draftId, adminUserId)
                .map(v -> ResponseEntity.ok(toView(v)))
                .orElse(ResponseEntity.badRequest().body(Map.of("ok", false, "error", "invalid_transition")));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<Map<String, Object>> getDraft(@PathVariable String draftId) {
        return draftVersionStore.get(draftId)
                .map(v -> ResponseEntity.ok(toView(v)))
                .orElse(ResponseEntity.notFound().build());
    }

    private static Map<String, Object> toView(AiDraftVersionStore.DraftVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("draftId", v.draftId());
        m.put("version", v.version());
        m.put("domain", v.domain());
        m.put("status", v.status());
        m.put("promptVersion", v.promptVersion());
        m.put("documentJson", v.documentJson());
        m.put("reviewNote", v.reviewNote());
        m.put("updatedAtMs", v.updatedAtMs());
        m.put("history", v.history());
        return m;
    }
}
