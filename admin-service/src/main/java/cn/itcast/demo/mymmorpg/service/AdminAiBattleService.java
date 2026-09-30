package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.BattleStatsClient;
import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 战斗数值周报助手：拉取进程内统计 + 可解释建议（不自动改配置）。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminAiBattleService {

    public static final String PERM_BATTLE_ANALYZE = "import:activity";

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final BattleStatsClient battleStatsClient;
    private final AiModelClient aiModelClient;
    private final AdminAiProperties aiProperties;

    public AdminAiBattleService(AdminPermissionService adminPermissionService,
                                AdminOperationLogService operationLogService,
                                BattleStatsClient battleStatsClient,
                                AiModelClient aiModelClient,
                                AdminAiProperties aiProperties) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.battleStatsClient = battleStatsClient;
        this.aiModelClient = aiModelClient;
        this.aiProperties = aiProperties;
    }

    public Map<String, Object> weeklyReport(Long adminUserId, String focus) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_BATTLE_ANALYZE)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_BATTLE_ANALYZE);
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        BattleStatsSnapshot snapshot = battleStatsClient.snapshot();
        String markdown = buildMarkdownReport(snapshot, focus);
        List<String> hypotheses = buildHypotheses(snapshot, focus);
        List<String> simCommands = List.of(
                "selftest",
                "battle damage --attack 65 --defense 4 --action-type 1",
                "battle damage --attack 80 --defense 4 --action-type 2 --skill-id 1001",
                "flow --name battle --player-level 10 --max-turns 15",
                "flow --name battle --player-level 35 --max-turns 20");
        List<String> warnings = new ArrayList<>();
        if (snapshot.getNotes() != null && snapshot.getNotes().stream().anyMatch(n -> n.contains("Redis 周表"))) {
            warnings.add("统计优先读 Redis 周表（跨实例累加，带 TTL）；进程内存仅作补充样本。");
        } else {
            warnings.add("当前无 Redis 周表样本，统计来自 battle-service 进程内存；配置 Redis 后可跨实例续存。");
        }
        warnings.add("建议仅作调参假设，不自动 apply 配置。");

        String modelUsed = "rule";
        Optional<String> llm = aiModelClient.generateBattleReportSummary(markdown);
        if (llm.isPresent()) {
            markdown = markdown + "\n\n## 模型补充说明\n\n" + llm.get().trim();
            modelUsed = aiProperties.getModel();
        } else if (aiProperties.isEnabled()) {
            warnings.add("外部模型不可用，已仅输出规则报告");
        }

        operationLogService.record(adminUserId, "AI_BATTLE_WEEKLY_REPORT", null,
                truncate("trace=" + traceId + ",ended=" + snapshot.getEndedTotal()
                        + ",winRate=" + String.format(Locale.ROOT, "%.3f", snapshot.getWinRate())
                        + ",model=" + modelUsed));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("traceId", traceId);
        resp.put("model", modelUsed);
        resp.put("promptVersion", aiProperties.getPromptVersion());
        resp.put("snapshot", snapshot);
        resp.put("markdown", markdown);
        resp.put("hypotheses", hypotheses);
        resp.put("simulationCommands", simCommands);
        resp.put("warnings", warnings);
        return resp;
    }

    static String buildMarkdownReport(BattleStatsSnapshot s, String focus) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 战斗数值周报\n\n");
        sb.append("- 数据窗口起点: ").append(s.getCollectedSinceEpochMs()).append('\n');
        sb.append("- 快照时间: ").append(s.getSnapshotEpochMs()).append('\n');
        sb.append("- 已结算场次: ").append(s.getEndedTotal()).append('\n');
        sb.append("- 胜/负/平: ").append(s.getWinCount()).append('/')
                .append(s.getLoseCount()).append('/').append(s.getDrawCount()).append('\n');
        sb.append("- 胜率: ").append(String.format(Locale.ROOT, "%.2f%%", s.getWinRate() * 100)).append('\n');
        sb.append("- 平均时长(秒): ").append(String.format(Locale.ROOT, "%.2f", s.getAvgDurationSec())).append('\n');
        sb.append("- 活跃绑定: ").append(s.getActiveBattleBindings())
                .append("（观测峰值 ").append(s.getMaxActiveBindingsObserved()).append("）\n");
        sb.append("- issuedBattleIdMax: ").append(s.getIssuedBattleIdMax()).append('\n');
        if (focus != null && !focus.isBlank()) {
            sb.append("- 关注点: ").append(focus.trim()).append('\n');
        }
        sb.append("\n## 按怪物模板\n\n");
        if (s.getByMonsterTemplate() == null || s.getByMonsterTemplate().isEmpty()) {
            sb.append("_暂无按模板样本_\n");
        } else {
            sb.append("| templateId | total | wins | winRate |\n|---|---:|---:|---:|\n");
            s.getByMonsterTemplate().forEach((id, agg) -> sb.append("| ").append(id)
                    .append(" | ").append(agg.getTotal())
                    .append(" | ").append(agg.getWins())
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f%%", agg.getWinRate() * 100))
                    .append(" |\n"));
        }
        sb.append("\n## 技能使用率 Top\n\n");
        if (s.getTopSkillUsage() == null || s.getTopSkillUsage().isEmpty()) {
            sb.append("_暂无技能释放样本_\n");
        } else {
            sb.append("| skillId | casts | usageRate |\n|---|---:|---:|\n");
            s.getTopSkillUsage().forEach(u -> sb.append("| ").append(u.getSkillId())
                    .append(" | ").append(u.getCastCount())
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f%%", u.getUsageRate() * 100))
                    .append(" |\n"));
        }
        sb.append("\n## 数据出处\n\n");
        sb.append("- 查询: `GET /internal/battle/stats/snapshot`\n");
        sb.append("- 写入点: `BattleService.handleBattleEnd` → `BattleStatsCollector.recordEnded`\n");
        if (s.getNotes() != null) {
            for (String n : s.getNotes()) {
                sb.append("- ").append(n).append('\n');
            }
        }
        return sb.toString();
    }

    static List<String> buildHypotheses(BattleStatsSnapshot s, String focus) {
        List<String> list = new ArrayList<>();
        if (s.getEndedTotal() < 20) {
            list.add("样本不足（<" + 20 + "），胜率波动大，建议先用 CLI flow 回放补样本再下结论。");
        }
        if (s.getWinRate() >= 0.7 && s.getEndedTotal() >= 20) {
            list.add("整体胜率偏高：可检查怪物防御/HP 或玩家普攻伤害曲线；假设 A：降低玩家攻击成长 5%。");
            list.add("假设 B：提高常见怪模板防御 8%，预期胜率向 55%~65% 回落。");
        } else if (s.getWinRate() <= 0.35 && s.getEndedTotal() >= 20) {
            list.add("整体胜率偏低：检查技能冷却/治疗道具收益；假设 A：技能倍率 +10%。");
            list.add("假设 B：降低精英怪攻击 5%，并观察平均时长是否下降。");
        } else {
            list.add("胜率处于常见区间：可按怪物模板对比异常点，优先调差异最大的 template。");
        }
        if (s.getAvgDurationSec() > 120 && s.getEndedTotal() > 0) {
            list.add("平均时长偏长：可能爆发不足或治疗过多，建议用 `battle damage` 对比普攻/技能期望伤害。");
        }
        if (focus != null && focus.contains("战士")) {
            list.add("关注「战士」：当前快照无职业维度，请结合匹配/职业日志二次切片（后续可扩展）。");
        }
        return list;
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }
}
