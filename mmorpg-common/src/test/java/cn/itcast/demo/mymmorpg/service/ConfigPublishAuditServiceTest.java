package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigPublishAuditServiceTest {

    @Test
    public void record_tracksHistoryAndLastSuccessful() {
        ConfigPublishAuditService audit = new ConfigPublishAuditService();
        audit.record("ops", "importJson", List.of("ActivityConfigs.json"), true, true, "validated");
        ConfigPublishAuditService.PublishRecord ok =
                audit.record("ops", "reloadAll", List.of("activity", "update"), false, true, "ok");
        audit.record("ops", "reloadAll", List.of(), false, false, "boom");

        assertThat(audit.lastSuccessful()).isEqualTo(ok);
        assertThat(audit.recent(10)).hasSize(3);
        assertThat(audit.toMap(ok).get("version")).isEqualTo(ok.version());
        assertThat(audit.toMap(ok).get("action")).isEqualTo("reloadAll");
    }
}
