package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.entity.Complaint;
import cn.itcast.demo.mymmorpg.repository.ComplaintRepository;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import cn.itcast.demo.mymmorpg.service.ai.ComplaintClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 运营投诉 AI 助手：归类 + 话术 + 建议动作（只读，不自动结案）。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminAiComplaintService {

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final ComplaintRepository complaintRepository;
    private final AiModelClient aiModelClient;
    private final AdminAiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AdminAiComplaintService(AdminPermissionService adminPermissionService,
                                   AdminOperationLogService operationLogService,
                                   ComplaintRepository complaintRepository,
                                   AiModelClient aiModelClient,
                                   AdminAiProperties aiProperties,
                                   ObjectMapper objectMapper) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.complaintRepository = complaintRepository;
        this.aiModelClient = aiModelClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> suggest(Long adminUserId, Long complaintId, String content) {
        if (!adminPermissionService.hasPermission(adminUserId, AdminComplaintService.PERM_COMPLAINT_HANDLE)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + AdminComplaintService.PERM_COMPLAINT_HANDLE);
        }

        Long playerId = null;
        String status = null;
        String resolvedContent = content;
        if (complaintId != null) {
            Complaint complaint = complaintRepository.findById(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("投诉单不存在: " + complaintId));
            resolvedContent = complaint.getContent();
            playerId = complaint.getPlayerId();
            status = complaint.getStatus().name();
        }
        if (resolvedContent == null || resolvedContent.isBlank()) {
            throw new IllegalArgumentException("请提供 complaintId 或 content");
        }

        String traceId = UUID.randomUUID().toString().replace("-", "");
        ComplaintClassifier.Advice local = ComplaintClassifier.classify(resolvedContent);
        List<String> warnings = new ArrayList<>(local.warnings());
        String modelUsed = "rule";
        String category = local.category();
        String priority = local.priority();
        String script = local.suggestedScript();
        List<String> checks = new ArrayList<>(local.relatedChecks());
        List<String> actions = new ArrayList<>(local.suggestedActions());

        Optional<String> llm = aiModelClient.generateComplaintAdvice(resolvedContent);
        if (llm.isPresent()) {
            try {
                JsonNode node = objectMapper.readTree(llm.get());
                category = textOr(node, "category", category);
                priority = textOr(node, "priority", priority);
                script = textOr(node, "suggestedScript", script);
                mergeArray(node.path("relatedChecks"), checks);
                mergeArray(node.path("suggestedActions"), actions);
                mergeArray(node.path("warnings"), warnings);
                modelUsed = aiProperties.getModel();
            } catch (Exception e) {
                warnings.add("模型输出解析失败，已使用本地规则建议");
            }
        } else if (aiProperties.isEnabled()) {
            warnings.add("外部模型不可用，已使用本地规则建议");
        }

        warnings.add("本接口仅生成建议，不会自动结案或执行发奖/禁言；请人工确认后走现有 /admin/complaints/{id}/handle");

        operationLogService.record(adminUserId, "AI_COMPLAINT_SUGGEST", complaintId,
                truncate("trace=" + traceId + ",cat=" + category + ",pri=" + priority + ",model=" + modelUsed));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("traceId", traceId);
        resp.put("complaintId", complaintId);
        resp.put("playerId", playerId);
        resp.put("complaintStatus", status);
        resp.put("category", category);
        resp.put("priority", priority);
        resp.put("suggestedScript", script);
        resp.put("relatedChecks", checks);
        resp.put("suggestedActions", actions);
        resp.put("warnings", warnings);
        resp.put("model", modelUsed);
        resp.put("promptVersion", aiProperties.getPromptVersion());
        return resp;
    }

    public Map<String, Object> batchTriage(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, AdminComplaintService.PERM_COMPLAINT_HANDLE)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + AdminComplaintService.PERM_COMPLAINT_HANDLE);
        }
        List<Complaint> pending = complaintRepository.findAll().stream()
                .filter(c -> c.getStatus() == Complaint.Status.PENDING)
                .toList();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (Complaint c : pending) {
            ComplaintClassifier.Advice advice = ComplaintClassifier.classify(c.getContent());
            categoryCounts.merge(advice.category(), 1, Integer::sum);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintId", c.getId());
            item.put("playerId", c.getPlayerId());
            item.put("category", advice.category());
            item.put("priority", advice.priority());
            item.put("summary", truncate(c.getContent(), 80));
            items.add(item);
        }
        operationLogService.record(adminUserId, "AI_COMPLAINT_TRIAGE", null,
                truncate("pending=" + pending.size() + ",cats=" + categoryCounts));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("count", items.size());
        resp.put("categoryCounts", categoryCounts);
        resp.put("items", items);
        resp.put("model", "rule");
        return resp;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.asText().isBlank() ? fallback : v.asText();
    }

    private static void mergeArray(JsonNode arr, List<String> target) {
        if (arr == null || !arr.isArray()) {
            return;
        }
        for (JsonNode n : arr) {
            String t = n.asText();
            if (t != null && !t.isBlank() && !target.contains(t)) {
                target.add(t);
            }
        }
    }

    private static String truncate(String detail) {
        return truncate(detail, 512);
    }

    private static String truncate(String detail, int max) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= max ? detail : detail.substring(0, max);
    }
}
