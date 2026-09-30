package cn.itcast.demo.mymmorpg.gacha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class GachaConfigService {

    private static final Logger log = LoggerFactory.getLogger(GachaConfigService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String configPath;
    private volatile List<GachaBannerConfig> banners = List.of();

    public GachaConfigService(ObjectMapper objectMapper,
                              ResourceLoader resourceLoader,
                              @Value("${game.gacha.config-path:config/gacha/Banners.json}") String configPath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.configPath = configPath;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public boolean reload() {
        try {
            BannersFile file = readFile();
            List<GachaBannerConfig> list = file == null || file.banners == null
                    ? List.of() : List.copyOf(file.banners);
            banners = list;
            log.info("Gacha banners loaded: {}", list.size());
            return true;
        } catch (Exception e) {
            log.error("Gacha banner reload failed", e);
            return false;
        }
    }

    public List<GachaBannerConfig> listActive(long now) {
        List<GachaBannerConfig> out = new ArrayList<>();
        for (GachaBannerConfig b : banners) {
            if (b != null && b.isActive(now)) {
                out.add(b);
            }
        }
        return out;
    }

    public GachaBannerConfig findByType(int bannerType, long now) {
        for (GachaBannerConfig b : listActive(now)) {
            if (b.getBannerType() == bannerType) {
                return b;
            }
        }
        return null;
    }

    public GachaBannerConfig findNormal(long now) {
        return findByType(GachaBannerType.NORMAL, now);
    }

    private BannersFile readFile() throws Exception {
        Path path = Path.of(configPath);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                return objectMapper.readValue(in, BannersFile.class);
            }
        }
        Resource resource = resourceLoader.getResource("classpath:" + configPath);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, BannersFile.class);
            }
        }
        Path alt = Path.of("config/gacha/Banners.json");
        if (Files.isRegularFile(alt)) {
            try (InputStream in = Files.newInputStream(alt)) {
                return objectMapper.readValue(in, BannersFile.class);
            }
        }
        log.warn("Banners.json not found");
        return new BannersFile();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BannersFile {
        public List<GachaBannerConfig> banners = new ArrayList<>();
    }
}
