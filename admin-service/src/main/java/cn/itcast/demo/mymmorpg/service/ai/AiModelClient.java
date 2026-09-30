package cn.itcast.demo.mymmorpg.service.ai;

import cn.itcast.demo.mymmorpg.ai.AiCostMeter;
import cn.itcast.demo.mymmorpg.ai.LlmHttpClient;
import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OpenAI 兼容 Chat Completions 客户端：超时、重试、Token 计量；未启用或无密钥时不注册。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AiModelClient {

    private static final String SYSTEM_PROMPT = """
            你是 MyMmorpg 活动配置助手，只能输出符合 ActivityImportDocument 的单个 JSON 对象（不要 Markdown 代码块）。
            未知字段不要臆造；无法满足时在顶层增加 "_warnings" 字符串数组说明。
            时间使用毫秒时间戳；条件表达式 type 仅允许 TOKEN_MIN、RECHARGE_MIN、STAGE_MIN、SIGN_DAY_MIN、LEVEL_MIN、VIP_MIN、CUSTOM。
            rewardMethod 仅允许 1/2/3/4。必须包含 type、name、startTime、endTime、rewardTiers。
            """;

    private static final String COMPLAINT_SYSTEM_PROMPT = """
            你是 MyMmorpg 运营助手。根据投诉原文输出 JSON（不要 Markdown）：
            {"category":"PAYMENT|TECHNICAL|ABUSE|ACCOUNT|ACTIVITY_REWARD|GENERAL",
             "priority":"P0|P1|P2",
             "suggestedScript":"对玩家回复话术",
             "relatedChecks":["只读核查项"],
             "suggestedActions":["待审批建议，勿直接执行写操作"],
             "warnings":["风险提示"]}
            不要输出可执行 SQL；不要要求完整支付凭证或明文密码。
            """;

    private static final String BATTLE_SYSTEM_PROMPT = """
            你是 MyMmorpg 战斗数值分析助手。基于已给出的统计报告补充 3 条以内可解释调参假设，
            用中文 Markdown 短文输出，必须引用报告中的数字；不要编造未提供的职业/DPS 明细；不要要求直接改生产配置。
            """;

    private static final String QUEST_SYSTEM_PROMPT = """
            你是 MyMmorpg 任务文案助手。根据策划需求输出单个 JSON（不要 Markdown）：
            {"questId":number,"name":"...","type":"MAIN|DAILY","description":"...","sceneHint":"...","npcHint":"...",
             "objectives":[{"type":"TALK_NPC|REACH_SCENE|KILL","target":"...","count":1,"text":"..."}],
             "rewards":[{"itemId":number,"count":number}],"sceneDetail":"场景氛围描写"}
            不要编造不存在的支付/账号接口；文案用中文。
            """;

    private final AdminAiProperties properties;
    private final AiCostMeter costMeter;
    private final AiMetrics aiMetrics;
    private final LlmHttpClient llm;

    public AiModelClient(AdminAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.costMeter = new AiCostMeter();
        this.costMeter.setBudgetTokens(properties.getTokenBudget());
        this.aiMetrics = new AiMetrics();
        this.llm = new LlmHttpClient(objectMapper, new LlmHttpClient.Config(
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getModel(),
                Math.max(1_000L, properties.getTimeoutMs()),
                Math.max(0, properties.getMaxRetries()),
                costMeter));
    }

    public boolean isAvailable() {
        return properties.isEnabled() && llm.isAvailable() && !costMeter.isOverBudget();
    }

    public AiCostMeter costMeter() {
        return costMeter;
    }

    public AiMetrics aiMetrics() {
        return aiMetrics;
    }

    public Optional<String> generateActivityJson(String template, String userPrompt) {
        return chat(SYSTEM_PROMPT, "模板=" + template + "\n需求：\n" + userPrompt, true);
    }

    public Optional<String> generateComplaintAdvice(String complaintContent) {
        return chat(COMPLAINT_SYSTEM_PROMPT, "投诉原文：\n" + complaintContent, true);
    }

    public Optional<String> generateBattleReportSummary(String markdownReport) {
        return chat(BATTLE_SYSTEM_PROMPT, "统计报告：\n" + markdownReport, false);
    }

    public Optional<String> generateQuestDraft(String userPrompt) {
        return chat(QUEST_SYSTEM_PROMPT, "需求：\n" + userPrompt, true);
    }

    private Optional<String> chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        long t0 = System.nanoTime();
        Optional<String> out = llm.chat(systemPrompt, userPrompt, jsonMode);
        aiMetrics.recordLlm(out.isPresent(), System.nanoTime() - t0);
        return out;
    }
}
