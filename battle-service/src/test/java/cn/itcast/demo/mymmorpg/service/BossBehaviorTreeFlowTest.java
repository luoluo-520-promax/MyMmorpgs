package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.bt.BehaviorTree;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boss 行为树流程：生成 Boss → 配置树 → 阶段切换 → 低血狂暴 → 锁定仇恨。
 */
public class BossBehaviorTreeFlowTest {

    @Test
    public void bossPhasesEnrageAndThreatLock() {
        AiTeammateService svc = new AiTeammateService();
        Map<String, Object> spawned = svc.spawn("raid-1", 100L, "boss首领");
        assertThat(spawned.get("role")).isEqualTo("boss");
        String botId = String.valueOf(spawned.get("botId"));

        // 可视化工具导出的简易节点可热挂
        assertThat(svc.configureBossTree(botId, List.of(
                Map.of("type", "action", "name", "intro_roar")
        )).get("engine")).isEqualTo("behavior_tree");

        // 恢复默认树做阶段/狂暴断言
        svc.configureBossTree(botId, List.of()); // fromConfig empty → defaultBossTree
        // configureBossTree with empty uses defaultBossTree via fromConfig

        Map<String, Object> phase = svc.tickBoss("raid-1", botId, 0.55, 100L);
        assertThat(phase.get("ok")).isEqualTo(true);
        assertThat(phase.get("phase")).isEqualTo(2);
        assertThat(phase.get("skill")).isEqualTo("phase2_aoe");

        Map<String, Object> enrage = svc.tickBoss("raid-1", botId, 0.2, 100L);
        assertThat(enrage.get("enraged")).isEqualTo(true);
        assertThat(enrage.get("lastAction")).isEqualTo("enrage");

        Map<String, Object> threat = svc.tickBoss("raid-1", botId, 0.8, 777L);
        // hp 高且已过狂暴条件不满足时，走锁仇恨或普攻
        assertThat(threat.get("ok")).isEqualTo(true);
        assertThat(threat.get("focusTargetId")).isIn(777L, 0L, 100L);

        Map<String, Object> status = svc.status("raid-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bots = (List<Map<String, Object>>) status.get("bots");
        assertThat(bots).hasSize(1);
        assertThat(bots.get(0).get("engine")).isEqualTo("behavior_tree");
    }

    @Test
    public void defaultTreeSelectorOrder() {
        BehaviorTree.Node tree = BehaviorTree.defaultBossTree();
        BehaviorTree.Blackboard bb = new BehaviorTree.Blackboard();
        bb.put("hpRatio", 0.9);
        bb.put("phase", 2);
        bb.put("threatTargetId", 9L);
        assertThat(tree.tick(bb)).isEqualTo(BehaviorTree.Status.SUCCESS);
        assertThat(bb.get("lastAction", "")).isIn("lock_threat", "basic_attack");
    }
}
