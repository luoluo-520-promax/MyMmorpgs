package cn.itcast.demo.mymmorpg.ai.threat;

import cn.itcast.demo.mymmorpg.ai.bt.BehaviorTreeDebugger;
import cn.itcast.demo.mymmorpg.routing.FunctionRouteTable;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仇恨表 + 行为树调试 + 功能号段路由业务流程。
 */
public class ThreatAndRouteFlowTest {

    @Test
    public void threatTable_hostBias_andDamageWeighted() {
        ThreatTable table = new ThreatTable();
        table.configure(ThreatTable.PriorityMode.DAMAGE_WEIGHTED, 1L, 1.5);
        long now = System.currentTimeMillis();
        table.addDamage(2L, 100, now);
        table.addDamage(1L, 80, now); // 房主 *1.5 = 120
        assertThat(table.topThreatTarget(0L)).isEqualTo(1L);

        table.configure(ThreatTable.PriorityMode.HOST, 1L, 1.0);
        assertThat(table.topThreatTarget(2L)).isEqualTo(1L);

        table.configure(ThreatTable.PriorityMode.ATTACKER, 1L, 1.0);
        assertThat(table.topThreatTarget(2L)).isEqualTo(2L);
    }

    @Test
    public void behaviorTreeDebugger_loadTickInspect() {
        BehaviorTreeDebugger dbg = new BehaviorTreeDebugger();
        dbg.loadFromConfig("boss-dbg", List.of(
                Map.of("type", "action", "name", "slash"),
                Map.of("type", "condition", "key", "enraged", "equals", "true")));
        dbg.blackboard("boss-dbg").put("hpRatio", 0.2);
        var trace = dbg.tick("boss-dbg", System.currentTimeMillis());
        assertThat(trace.status()).isNotBlank();
        Map<String, Object> inspect = dbg.gmInspect("boss-dbg");
        assertThat(inspect.get("ok")).isEqualTo(true);
        assertThat(inspect.get("loaded")).isEqualTo(true);
    }

    @Test
    public void functionRouteTable_sceneBagBattleRanges() {
        FunctionRouteTable table = new FunctionRouteTable();
        assertThat(table.resolve(105)).isEqualTo(FunctionRouteTable.TargetService.SCENE);
        assertThat(table.resolve(301)).isEqualTo(FunctionRouteTable.TargetService.BAG);
        assertThat(table.resolve(201)).isEqualTo(FunctionRouteTable.TargetService.BATTLE);
        assertThat(table.shouldBypassPlayerGateway(105)).isTrue();
        assertThat(table.shouldBypassPlayerGateway(10)).isFalse();
    }
}
