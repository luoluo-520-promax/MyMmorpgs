package cn.itcast.demo.mymmorpg.anticheat;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientIntegrityCheckerTest {

    @Test
    public void trustedWhenCleanFlags() {
        ClientIntegrityChecker checker = new ClientIntegrityChecker();
        ClientIntegrityChecker.IntegrityResult r = checker.check("fp-abc", Map.of(
                ClientIntegrityChecker.FLAG_ROOTED, false,
                ClientIntegrityChecker.FLAG_EMULATOR, false));
        assertThat(r.trusted()).isTrue();
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.reasons()).isEmpty();
    }

    @Test
    public void criticalFlagMakesUntrusted() {
        ClientIntegrityChecker checker = new ClientIntegrityChecker();
        ClientIntegrityChecker.IntegrityResult rooted = checker.check("fp", Map.of(
                ClientIntegrityChecker.FLAG_ROOTED, true));
        assertThat(rooted.trusted()).isFalse();
        assertThat(rooted.reasons()).contains(ClientIntegrityChecker.FLAG_ROOTED);

        ClientIntegrityChecker.IntegrityResult hooked = checker.check("fp", Map.of(
                "hooked", "true"));
        assertThat(hooked.trusted()).isFalse();

        ClientIntegrityChecker.IntegrityResult tampered = checker.check("fp", Map.of(
                ClientIntegrityChecker.FLAG_TAMPERED, 1));
        assertThat(tampered.trusted()).isFalse();

        ClientIntegrityChecker.IntegrityResult dbg = checker.check("fp", Map.of(
                ClientIntegrityChecker.FLAG_DEBUGGER, true));
        assertThat(dbg.trusted()).isFalse();
    }

    @Test
    public void emulatorSoftPenalty() {
        ClientIntegrityChecker checker = new ClientIntegrityChecker();
        checker.configure(20, 70);
        ClientIntegrityChecker.IntegrityResult soft = checker.check("fp", Map.of(
                ClientIntegrityChecker.FLAG_EMULATOR, true));
        assertThat(soft.trusted()).isTrue();
        assertThat(soft.score()).isEqualTo(80);
        assertThat(soft.reasons()).contains(ClientIntegrityChecker.FLAG_EMULATOR);

        checker.configure(40, 70);
        ClientIntegrityChecker.IntegrityResult heavy = checker.check("fp", Map.of(
                ClientIntegrityChecker.FLAG_EMULATOR, true));
        assertThat(heavy.trusted()).isFalse();
        assertThat(heavy.score()).isEqualTo(60);
        assertThat(heavy.reasons()).contains("score_below_threshold");
    }

    @Test
    public void missingFingerprintStillScored() {
        ClientIntegrityChecker checker = new ClientIntegrityChecker();
        ClientIntegrityChecker.IntegrityResult r = checker.check("  ", Map.of());
        assertThat(r.reasons()).contains("missing_device_fingerprint");
        assertThat(r.score()).isEqualTo(90);
        assertThat(r.trusted()).isTrue();
    }
}
