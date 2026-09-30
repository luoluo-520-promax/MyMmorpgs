package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import cn.itcast.demo.mymmorpg.service.ai.QuestDraftTemplateGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@EnableConfigurationProperties(AdminAiProperties.class)
public class AdminAiQuestService {

    public static final String PERM_AI_QUEST = "import:quest";

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final AdminAiProperties aiProperties;
    private final AiModelClient aiModelClient;
    private final QuestDraftTemplateGenerator templateGenerator;
    private final ObjectMapper objectMapper;

    public AdminAiQuestService(AdminPermissionService adminPermissionService,
                               AdminOperationLogService operationLogService,
                               AdminAiProperties aiProperties,
                               AiModelClient aiModelClient,
                               ObjectMapper objectMapper) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.aiProperties = aiProperties;
        this.aiModelClient = aiModelClient;
        this.objectMapper = objectMapper;
        this.templateGenerator = new QuestDraftTemplateGenerator(objectMapper);
    }

    public Map<String, Object> draft(Long adminUserId, String prompt, boolean dryRun) {
        requirePermission(adminUserId);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        List<String> warnings = new ArrayList<>();
        String modelUsed = "template";
        ObjectNode document;

        Optional<String> llmJson = aiModelClient.generateQuestDraft(prompt);
        if (llmJson.isPresent()) {
            try {
                JsonNode parsed = objectMapper.readTree(llmJson.get());
                if (parsed instanceof ObjectNode obj) {
                    document = obj;
                    modelUsed = aiProperties.getModel();
                } else {
                    warnings.add("模型返回非对象 JSON，已回退本地模板");
                    document = templateGenerator.generate(prompt);
                }
            } catch (Exception e) {
                warnings.add("模型输出解析失败，已回退本地模板");
                document = templateGenerator.generate(prompt);
            }
        } else {
            if (aiProperties.isEnabled()) {
                warnings.add("外部模型不可用或未配置密钥，已使用本地模板生成");
            }
            document = templateGenerator.generate(prompt);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("traceId", traceId);
        result.put("dryRun", dryRun);
        result.put("modelUsed", modelUsed);
        result.put("document", document);
        result.put("warnings", warnings);
        result.put("message", dryRun ? "仅草案预览，未写入 quest 配置" : "草案已生成（写入请走任务导入流程）");
        operationLogService.record(adminUserId, "AI_QUEST_DRAFT",
                (long) document.path("questId").asInt(0),
                "traceId=" + traceId + ",dryRun=" + dryRun + ",model=" + modelUsed);
        return result;
    }

    private void requirePermission(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_AI_QUEST)
                && !adminPermissionService.hasPermission(adminUserId, AdminAiActivityService.PERM_AI_ACTIVITY)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_AI_QUEST);
        }
    }
}
