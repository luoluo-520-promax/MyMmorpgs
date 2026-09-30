package cn.itcast.demo.mymmorpg.model.update;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manifest HMAC 签名验签。
 */
public class ManifestSignatureUtilTest {

    @Test
    public void signAndVerify_roundTrip() {
        String payload = "{\"versionCode\":\"1.0.0\",\"manifestChecksum\":\"abc\"}";
        String secret = "unit-test-secret";
        String sig = ManifestSignatureUtil.signHmacSha256(payload, secret);
        assertThat(sig).hasSize(64);
        assertThat(ManifestSignatureUtil.verify(payload, secret, sig)).isTrue();
        assertThat(ManifestSignatureUtil.verify(payload, secret, "deadbeef")).isFalse();
        assertThat(ManifestSignatureUtil.verify(payload + "x", secret, sig)).isFalse();
    }

    @Test
    public void sign_emptySecretReturnsEmpty() {
        assertThat(ManifestSignatureUtil.signHmacSha256("x", "")).isEmpty();
        assertThat(ManifestSignatureUtil.verify("x", "s", "")).isFalse();
    }
}
