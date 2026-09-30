package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminPlayerPredictionServiceTest {

    private static AdminAiPlatformBridge testBridge() {
        return new AdminAiPlatformBridge(false, EmptyAiPlatformClientProvider.INSTANCE);
    }

    @Test
    public void predict_returnsActionHook() {
        AdminPlayerPredictionService svc = new AdminPlayerPredictionService(testBridge());
        Map<String, Object> out = svc.predict(88L, Map.of(
                "loginDays7", 1,
                "questCompletionRate", 0.1,
                "onlineMinutes7", 10,
                "daysSinceLastLogin", 8));
        assertThat(out.get("ok")).isEqualTo(true);
        assertThat(out.get("suggestedAction")).isNotNull();
        assertThat(out.get("activityHook")).isInstanceOf(Map.class);
        assertThat(out.get("intervention")).isInstanceOf(Map.class);
    }
}
