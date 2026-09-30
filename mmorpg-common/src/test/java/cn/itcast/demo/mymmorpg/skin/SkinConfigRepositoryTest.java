package cn.itcast.demo.mymmorpg.skin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SkinConfigRepositoryTest {

    @Test
    public void reload_loadsDefaultSkinFromClasspathOrFile() {
        SkinConfigRepository repo = new SkinConfigRepository(
                new ObjectMapper(),
                new DefaultResourceLoader(),
                "config/skin/SkinConfigs.json");
        assertThat(repo.reload()).isTrue();
        assertThat(repo.listEnabled())
                .as("expected classpath:config/skin/SkinConfigs.json or repo config/skin/")
                .isNotEmpty();
        SkinConfig def = repo.listEnabled().stream().filter(SkinConfig::isDefault).findFirst().orElse(null);
        assertThat(def).isNotNull();
        assertThat(repo.find(def.skinId())).isNotNull();
        SkinConfig paid = repo.findByItemId(71003);
        assertThat(paid).isNotNull();
        assertThat(paid.skinId()).isEqualTo(1003);
    }
}
