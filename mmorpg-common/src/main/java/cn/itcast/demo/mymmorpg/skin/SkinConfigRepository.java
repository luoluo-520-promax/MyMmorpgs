package cn.itcast.demo.mymmorpg.skin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 皮肤静态配置：优先读 {@code config/skin/SkinConfigs.json}，否则 classpath。
 */
@Repository
public class SkinConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(SkinConfigRepository.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String configPath;

    private volatile Map<Integer, SkinConfig> bySkinId = Collections.emptyMap();
    private volatile Map<Integer, SkinConfig> byItemId = Collections.emptyMap();
    private volatile List<SkinConfig> allEnabled = List.of();

    public SkinConfigRepository(ObjectMapper objectMapper,
                                ResourceLoader resourceLoader,
                                @Value("${game.skin.config-path:config/skin/SkinConfigs.json}") String configPath) {
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
            SkinConfigsFile file = readFile();
            Map<Integer, SkinConfig> index = new HashMap<>();
            Map<Integer, SkinConfig> itemIndex = new HashMap<>();
            List<SkinConfig> enabled = new ArrayList<>();
            if (file != null && file.skins != null) {
                for (SkinConfig skin : file.skins) {
                    if (skin == null || skin.skinId() <= 0) {
                        continue;
                    }
                    index.put(skin.skinId(), skin);
                    if (skin.itemId() > 0) {
                        SkinConfig prev = itemIndex.put(skin.itemId(), skin);
                        if (prev != null) {
                            log.warn("Duplicate SkinConfig.itemId={} skinId={} vs {}",
                                    skin.itemId(), prev.skinId(), skin.skinId());
                        }
                    }
                    if (skin.isEnabled()) {
                        enabled.add(skin);
                    }
                }
            }
            bySkinId = Collections.unmodifiableMap(index);
            byItemId = Collections.unmodifiableMap(itemIndex);
            allEnabled = List.copyOf(enabled);
            log.info("Skin configs loaded: total={}, enabled={}, itemMapped={}",
                    index.size(), enabled.size(), itemIndex.size());
            return true;
        } catch (Exception e) {
            log.error("Skin config reload failed, keep previous snapshot", e);
            return false;
        }
    }

    public SkinConfig find(int skinId) {
        return bySkinId.get(skinId);
    }

    /** 按皮肤解锁道具 itemId 查找（付费皮）。 */
    public SkinConfig findByItemId(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return byItemId.get(itemId);
    }

    public List<SkinConfig> listEnabled() {
        return allEnabled;
    }

    private SkinConfigsFile readFile() throws Exception {
        Path path = Path.of(configPath);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                return objectMapper.readValue(in, SkinConfigsFile.class);
            }
        }
        Resource resource = resourceLoader.getResource("classpath:" + configPath);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, SkinConfigsFile.class);
            }
        }
        // 仓库根目录相对路径（本地开发）
        Path alt = Path.of("config/skin/SkinConfigs.json");
        if (Files.isRegularFile(alt)) {
            try (InputStream in = Files.newInputStream(alt)) {
                return objectMapper.readValue(in, SkinConfigsFile.class);
            }
        }
        log.warn("SkinConfigs.json not found at {} or classpath", configPath);
        return new SkinConfigsFile();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkinConfigsFile {
        public List<SkinConfig> skins = new ArrayList<>();
    }
}
