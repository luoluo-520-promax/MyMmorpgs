package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AiTeammateStrategyTest {

    @Test
    public void setStrategyConservative_preferDefend() {
        AiTeammateService svc = new AiTeammateService();
        Map<String, Object> spawned = svc.spawn("b-s", 3L, "dps");
        String botId = String.valueOf(spawned.get("botId"));
        Map<String, Object> set = svc.setStrategy("b-s", botId, "CONSERVATIVE", Map.of("engageDistance", 5.0));
        assertThat(set.get("strategy")).isEqualTo("CONSERVATIVE");
        Map<String, Object> cmd = svc.command("b-s", botId, "");
        assertThat(cmd.get("lastAction")).isEqualTo("defend");
    }
}
