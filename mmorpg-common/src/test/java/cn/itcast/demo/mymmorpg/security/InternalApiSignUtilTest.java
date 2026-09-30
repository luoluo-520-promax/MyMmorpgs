package cn.itcast.demo.mymmorpg.security;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalApiSignUtilTest {

    private static final String SECRET = "test-internal-secret";

    @Test
    public void signAndVerify_success() {
        long timestamp = System.currentTimeMillis();
        byte[] body = new byte[] {1, 2, 3};
        String signature = InternalApiSignUtil.sign(SECRET, timestamp, "POST", "/internal/battle/start", 42L, body);

        assertThat(signature).hasSize(64);
        assertThat(InternalApiSignUtil.verify(
                SECRET, timestamp, "POST", "/internal/battle/start", 42L, body, signature, timestamp)).isTrue();
    }

    @Test
    public void verify_rejectsExpiredTimestamp() {
        long timestamp = System.currentTimeMillis() - InternalApiSignUtil.MAX_CLOCK_SKEW.toMillis() - 1_000;
        byte[] body = new byte[0];
        String signature = InternalApiSignUtil.sign(SECRET, timestamp, "POST", "/internal/activity/list", 1L, body);

        assertThat(InternalApiSignUtil.verify(
                SECRET, timestamp, "POST", "/internal/activity/list", 1L, body, signature, System.currentTimeMillis()))
                .isFalse();
    }

    @Test
    public void verify_rejectsTamperedPlayerId() {
        long timestamp = System.currentTimeMillis();
        byte[] body = new byte[0];
        String signature = InternalApiSignUtil.sign(SECRET, timestamp, "POST", "/internal/battle/end", 5L, body);

        assertThat(InternalApiSignUtil.verify(
                SECRET, timestamp, "POST", "/internal/battle/end", 99L, body, signature, timestamp)).isFalse();
    }
}
