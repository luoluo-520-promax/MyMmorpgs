package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RelicLockEnhanceTest {

    @Test
    public void lockedSubStatSkippedOnEnhance() {
        RelicRandomizer randomizer = new RelicRandomizer();
        RelicScoringService svc = new RelicScoringService(randomizer);
        RelicRandomizer.RelicRoll roll = new RelicRandomizer.RelicRoll(
                "gladiator",
                "ATK_PCT",
                20.0,
                List.of(
                        Map.of("stat", "CRIT_RATE", "value", 5.0),
                        Map.of("stat", "CRIT_DMG", "value", 10.0),
                        Map.of("stat", "ATK_FLAT", "value", 15.0),
                        Map.of("stat", "ER", "value", 8.0)),
                Set.of());
        svc.putRelic(1001L, roll);
        svc.creditLockConsumable(9L, 2);
        svc.creditEnhanceMaterial(9L, 5);

        Map<String, Object> lock = svc.lockSubStat(9L, 1001L, 0);
        assertThat(lock.get("ok")).isEqualTo(true);
        assertThat(lock.get("lockedIndexes")).isEqualTo(List.of(0));

        Map<String, Object> enhance = svc.enhance(9L, 1001L, 42L);
        assertThat(enhance.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) enhance.get("rollDetail");
        assertThat(detail.get("action")).isEqualTo("UPGRADE");
        assertThat(detail.get("index")).isNotEqualTo(0);
        assertThat(detail.get("skippedLocked")).isEqualTo(true);
    }

    @Test
    public void equipEnhanceRespectsLockedIndexes() {
        EquipRandomizer er = new EquipRandomizer(new ObjectMapper());
        EquipEnhanceService svc = new EquipEnhanceService(er, new ObjectMapper());
        svc.putMemoryItem(2002L, new EquipRandomizer.AffixRoll(
                "HP_PCT", 18.0,
                List.of(
                        Map.of("stat", "CRIT_RATE", "value", 4.0),
                        Map.of("stat", "CRIT_DMG", "value", 9.0),
                        Map.of("stat", "ATK_FLAT", "value", 12.0),
                        Map.of("stat", "EM", "value", 16.0))));
        svc.creditGold(1L, 100_000);
        svc.creditEnhanceMaterial(1L, 100);
        svc.creditLockConsumable(1L, 2);
        assertThat(svc.lockSubStat(1L, 2002L, 1).get("ok")).isEqualTo(true);

        Map<String, Object> last = Map.of();
        for (int i = 0; i < 4; i++) {
            last = svc.enhance(1L, 2002L, 99L);
        }
        assertThat(last.get("subStatRolled")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) last.get("rollDetail");
        if ("UPGRADE".equals(detail.get("action"))) {
            assertThat(detail.get("index")).isNotEqualTo(1);
        }
    }
}
