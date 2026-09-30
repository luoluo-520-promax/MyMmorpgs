package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EquipEnhanceServiceTest {

    @Test
    public void enhanceEveryFourLevelsGrowsSubStatWithSeed() {
        ObjectMapper mapper = new ObjectMapper();
        EquipRandomizer randomizer = new EquipRandomizer(mapper);
        EquipEnhanceService svc = new EquipEnhanceService(randomizer, mapper);
        long playerId = 42L;
        long itemUid = 9001L;
        EquipRandomizer.AffixRoll base = new EquipRandomizer.AffixRoll(
                "ATK_PCT", 12.0,
                List.of(
                        Map.of("stat", "CRIT_RATE", "value", 3.0),
                        Map.of("stat", "EM", "value", 5.0)));
        svc.putMemoryItem(itemUid, base);
        svc.creditGold(playerId, 100_000);
        svc.creditEnhanceMaterial(playerId, 100);

        Map<String, Object> last = Map.of();
        for (int i = 0; i < 4; i++) {
            last = svc.enhance(playerId, itemUid, 12345L);
            assertThat(last.get("ok")).isEqualTo(true);
        }
        assertThat(last.get("enhanceLevel")).isEqualTo(4);
        assertThat(last.get("subStatRolled")).isEqualTo(true);
        assertThat(((Map<?, ?>) last.get("rollDetail")).get("action")).isIn("ADD", "UPGRADE");

        // 相同种子 → 相同成长结果
        EquipEnhanceService svc2 = new EquipEnhanceService(randomizer, mapper);
        svc2.putMemoryItem(9002L, base);
        svc2.creditGold(playerId, 100_000);
        svc2.creditEnhanceMaterial(playerId, 100);
        Map<String, Object> a = null;
        Map<String, Object> b = null;
        svc.putMemoryItem(9003L, base);
        svc.creditGold(playerId, 100_000);
        svc.creditEnhanceMaterial(playerId, 100);
        for (int i = 0; i < 4; i++) {
            a = svc.enhance(playerId, 9003L, 999L);
            b = svc2.enhance(playerId, 9002L, 999L);
        }
        assertThat(a.get("rollDetail")).isEqualTo(b.get("rollDetail"));
    }
}
