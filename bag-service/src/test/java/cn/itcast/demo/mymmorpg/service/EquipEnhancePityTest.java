package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EquipEnhancePityTest {

    @Test
    public void pityTriggersAfterThreeMissesOnLockedSub() {
        ObjectMapper mapper = new ObjectMapper();
        EquipRandomizer randomizer = new EquipRandomizer(mapper);
        EquipEnhanceService svc = new EquipEnhanceService(randomizer, mapper);
        long playerId = 88L;
        long itemUid = 7001L;
        EquipRandomizer.AffixRoll base = new EquipRandomizer.AffixRoll(
                "ATK_PCT", 12.0,
                List.of(
                        Map.of("stat", "CRIT_RATE", "value", 3.0),
                        Map.of("stat", "EM", "value", 5.0),
                        Map.of("stat", "ATK_FLAT", "value", 10.0),
                        Map.of("stat", "DEF_PCT", "value", 2.0)));
        svc.putMemoryItem(itemUid, base);
        svc.creditGold(playerId, 200_000);
        svc.creditEnhanceMaterial(playerId, 200);
        svc.creditLockConsumable(playerId, 5);
        svc.lockSubStat(playerId, itemUid, 0);

        Map<String, Object> last = Map.of();
        for (int level = 1; level <= 16; level++) {
            last = svc.enhance(playerId, itemUid, level * 17L);
            assertThat(last.get("ok")).isEqualTo(true);
        }
        Map<?, ?> roll = (Map<?, ?>) last.get("rollDetail");
        if (roll != null && Boolean.TRUE.equals(roll.get("ENHANCE_PITY_TRIGGERED"))) {
            assertThat(last.get("ENHANCE_PITY_TRIGGERED")).isEqualTo(true);
            assertThat(last.get("clientVfx")).isEqualTo("GOLDEN_PITY_FLASH");
        }
    }
}
