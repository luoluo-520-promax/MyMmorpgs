package cn.itcast.demo.mymmorpg.abyss;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AbyssServiceTest {

    private AbyssService abyssService;

    @BeforeMethod
    public void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        AbyssFloorConfigLoader loader = new AbyssFloorConfigLoader(
                mapper, new DefaultResourceLoader(), "classpath:config/abyss/abyss_floor_config.json");
        ObjectProvider empty = Mockito.mock(ObjectProvider.class);
        Mockito.when(empty.getIfAvailable()).thenReturn(null);
        abyssService = new AbyssService(loader, empty, empty, mapper);
    }

    @Test
    public void starsForRemainThresholds() {
        assertThat(AbyssService.starsForRemain(60)).isEqualTo(3);
        assertThat(AbyssService.starsForRemain(30)).isEqualTo(2);
        assertThat(AbyssService.starsForRemain(10)).isEqualTo(1);
    }

    @Test
    public void chamberChainKeepsHpAndAccumulatesStars() {
        Map<String, Object> start = abyssService.start(42L);
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(start.get("floorIndex")).isEqualTo(1);
        assertThat(start.get("chamberIndex")).isEqualTo(1);
        assertThat(((Map<?, ?>) start.get("blessing")).get("type")).isEqualTo("CHARGE_ATTACK");

        Map<String, Object> finish1 = abyssService.finishChamber(
                42L, true, 8000, 50, List.of(1200, 0), 65);
        assertThat(finish1.get("starsGained")).isEqualTo(3);
        assertThat(finish1.get("hp")).isEqualTo(8000);
        assertThat(finish1.get("chamberIndex")).isEqualTo(2);

        Map<String, Object> finish2 = abyssService.finishChamber(
                42L, true, 7500, 40, List.of(500), 35);
        assertThat(finish2.get("starsGained")).isEqualTo(2);
        assertThat(finish2.get("totalStars")).isEqualTo(5);
        assertThat(finish2.get("hp")).isEqualTo(7500);
    }

    @Test
    public void milestoneAttachments() {
        assertThat(AbyssService.attachmentsFor(9)).contains("50");
        assertThat(AbyssService.attachmentsFor(15)).contains("30001");
    }
}
