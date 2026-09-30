package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EquipRandomizerTest {

    @Test
    public void rollAndRoundTripBlob() {
        EquipRandomizer randomizer = new EquipRandomizer(new ObjectMapper());
        EquipRandomizer.AffixRoll roll = randomizer.roll(2001, 4);
        assertThat(roll.mainStat()).isIn(EquipRandomizer.MAIN_STATS);
        assertThat(roll.subStats()).isNotEmpty();
        byte[] blob = randomizer.toBlob(roll);
        EquipRandomizer.AffixRoll back = randomizer.fromBlob(blob);
        assertThat(back.mainStat()).isEqualTo(roll.mainStat());
        assertThat(back.subStats()).hasSize(roll.subStats().size());
    }
}
