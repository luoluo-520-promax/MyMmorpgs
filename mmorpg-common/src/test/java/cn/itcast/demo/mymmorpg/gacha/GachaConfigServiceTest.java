package cn.itcast.demo.mymmorpg.gacha;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GachaConfigServiceTest {

    @Test
    public void reload_loadsClasspathBanners() {
        GachaConfigService svc = new GachaConfigService(
                new ObjectMapper(),
                new DefaultResourceLoader(),
                "config/gacha/Banners.json");
        assertThat(svc.reload()).isTrue();
        assertThat(svc.listActive(System.currentTimeMillis())).isNotEmpty();
        assertThat(svc.findByType(GachaBannerType.NORMAL, System.currentTimeMillis())).isNotNull();
    }
}
