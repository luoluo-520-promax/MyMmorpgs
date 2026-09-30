package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.bt.BehaviorTree;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 队友完整业务：召唤 → 策略切换 → 空指令按策略行动 → Boss BT 热更 tick → 指标。
 */
public class AiTeammateBusinessFlowTest {

    @Test
    public void strategySwitchAndBossBtHotReloadFlow() {
        AiTeammateService svc = new AiTeammateService();

        Map<String, Object> dps = svc.spawn("biz-1", 501L, "进攻输出");
        assertThat(dps.get("strategy")).isEqualTo("AGGRESSIVE");
        String dpsId = String.valueOf(dps.get("botId"));

        Map<String, Object> set = svc.setStrategy("biz-1", dpsId, "SUPPORT",
                Map.of("healThreshold", 0.8));
        assertThat(set.get("ok")).isEqualTo(true);
        assertThat(set.get("strategy")).isEqualTo("SUPPORT");
        assertThat(set.get("strategyParams")).isInstanceOf(Map.class);

        Map<String, Object> auto = svc.command("biz-1", dpsId, "");
        assertThat(auto.get("lastAction")).isEqualTo("follow");

        Map<String, Object> boss = svc.spawn("biz-1", 501L, "boss");
        String bossId = String.valueOf(boss.get("botId"));
        assertThat(boss.get("engine")).isEqualTo("behavior_tree");

        List<Map<String, Object>> nodes = List.of(
                Map.of("type", "action", "name", "custom_slam", "skill", "slam"));
        assertThat(svc.configureBossTree(bossId, nodes).get("ok")).isEqualTo(true);

        Map<String, Object> tick = svc.tickBoss("biz-1", bossId, 0.15, 501L);
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(tick.get("decisionReason")).asString().contains("bt:");

        Map<String, Object> status = svc.status("biz-1");
        assertThat(status.get("aiMetrics")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) status.get("aiMetrics");
        assertThat(((Number) metrics.get("btTicks")).longValue()).isGreaterThan(0);

        assertThat(svc.setStrategy("biz-1", dpsId, "NOPE", null).get("error"))
                .isEqualTo("invalid_strategy");
    }

    @Test
    public void btDebuggerLoadTickInspectFlow() {
        AiTeammateService svc = new AiTeammateService();
        assertThat(svc.loadBehaviorTree("tree-a", null).get("ok")).isEqualTo(true);
        Map<String, Object> tick = svc.tickBehaviorTree("tree-a", 0.25, 9L);
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(tick.get("status")).isIn(
                BehaviorTree.Status.SUCCESS.name(),
                BehaviorTree.Status.FAILURE.name(),
                BehaviorTree.Status.RUNNING.name());
        Map<String, Object> inspect = svc.inspectBehaviorTree("tree-a");
        assertThat(inspect).isNotEmpty();
    }
}
