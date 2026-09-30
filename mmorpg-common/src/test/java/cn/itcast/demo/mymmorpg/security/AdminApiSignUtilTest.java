package cn.itcast.demo.mymmorpg.security;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminApiSignUtilTest {

    private static final String SECRET = "test-admin-secret";

    @Test
    public void signAndVerify_success() {
        long timestamp = System.currentTimeMillis();
        byte[] body = "{\"versionCode\":\"1.0.0\"}".getBytes();
        String signature = AdminApiSignUtil.sign(
                SECRET, timestamp, "POST", "/admin/import/manifests", 9L, body);

        assertThat(signature).hasSize(64);
        assertThat(AdminApiSignUtil.verify(
                SECRET, timestamp, "POST", "/admin/import/manifests", 9L, body, signature, timestamp))
                .isTrue();
    }

    @Test
    public void verify_rejectsBlankSecret() {
        long timestamp = System.currentTimeMillis();
        assertThat(AdminApiSignUtil.verify(
                "", timestamp, "POST", "/admin/import/activities", 1L, new byte[0], "abc", timestamp))
                .isFalse();
    }
}
