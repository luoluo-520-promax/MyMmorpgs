package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AiTeammateServiceTest {

    @Test
    public void spawnAndCommandFocus() {
        AiTeammateService svc = new AiTeammateService();
        Map<String, Object> spawned = svc.spawn("b-1", 9L, "治疗");
        assertThat(spawned.get("ok")).isEqualTo(true);
        assertThat(spawned.get("role")).isEqualTo("healer");
        String botId = String.valueOf(spawned.get("botId"));
        Map<String, Object> cmd = svc.command("b-1", botId, "集火Boss");
        assertThat(cmd.get("lastAction")).isEqualTo("focus_fire");
        assertThat(svc.status("b-1").get("ok")).isEqualTo(true);
    }

    @Test
    public void spawnBossUsesBehaviorTree() {
        AiTeammateService svc = new AiTeammateService();
        Map<String, Object> spawned = svc.spawn("b-boss", 1L, "boss");
        assertThat(spawned.get("role")).isEqualTo("boss");
        assertThat(spawned.get("engine")).isEqualTo("behavior_tree");
        String botId = String.valueOf(spawned.get("botId"));
        Map<String, Object> tick = svc.tickBoss("b-boss", botId, 0.2, 1L);
        assertThat(tick.get("ok")).isEqualTo(true);
        assertThat(tick.get("enraged")).isEqualTo(true);
    }
}
