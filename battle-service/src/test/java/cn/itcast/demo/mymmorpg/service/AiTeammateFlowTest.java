package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AI 队友：召唤 → 多指令指挥 → 状态查询。 */
public class AiTeammateFlowTest {

    @Test
    public void spawnCommandStatusFlow() {
        AiTeammateService svc = new AiTeammateService();
        Map<String, Object> dps = svc.spawn("battle-flow", 100L, "输出");
        Map<String, Object> healer = svc.spawn("battle-flow", 100L, "治疗位");
        assertThat(dps.get("role")).isEqualTo("dps");
        assertThat(healer.get("role")).isEqualTo("healer");

        String dpsId = String.valueOf(dps.get("botId"));
        String healId = String.valueOf(healer.get("botId"));

        assertThat(svc.command("battle-flow", dpsId, "集火目标").get("lastAction")).isEqualTo("focus_fire");
        assertThat(svc.command("battle-flow", healId, "治疗队友").get("lastAction")).isEqualTo("heal");
        assertThat(svc.command("battle-flow", dpsId, "散开").get("lastAction")).isEqualTo("spread");
        assertThat(svc.command("battle-flow", healId, "跟随我").get("lastAction")).isEqualTo("follow");
        assertThat(svc.command("battle-flow", dpsId, "防守").get("lastAction")).isEqualTo("defend");

        Map<String, Object> status = svc.status("battle-flow");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bots = (List<Map<String, Object>>) status.get("bots");
        assertThat(bots).hasSize(2);

        assertThat(svc.command("battle-flow", "missing", "集火").get("error")).isEqualTo("bot_not_found");
        assertThat(svc.command("no-battle", dpsId, "集火").get("error")).isEqualTo("battle_not_found");
    }
}
