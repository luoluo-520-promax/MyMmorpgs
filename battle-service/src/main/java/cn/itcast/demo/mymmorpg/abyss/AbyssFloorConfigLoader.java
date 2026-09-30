package cn.itcast.demo.mymmorpg.abyss;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class AbyssFloorConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(AbyssFloorConfigLoader.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String configPath;
    private volatile AbyssFloorConfigDocument document;

    public AbyssFloorConfigLoader(ObjectMapper objectMapper,
                                  ResourceLoader resourceLoader,
                                  @Value("${battle.abyss.config-path:classpath:config/abyss/abyss_floor_config.json}")
                                  String configPath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.configPath = configPath;
        reload();
    }

    public AbyssFloorConfigDocument current() {
        AbyssFloorConfigDocument doc = document;
        return doc == null ? fallback() : doc;
    }

    public synchronized void reload() {
        try {
            Resource resource = resourceLoader.getResource(configPath);
            try (InputStream in = resource.getInputStream()) {
                document = objectMapper.readValue(in, AbyssFloorConfigDocument.class);
            }
            log.info("abyss config loaded seasonId={} floors={}",
                    document.getSeasonId(), document.getFloors().size());
        } catch (Exception e) {
            log.warn("abyss config load failed, using fallback: {}", e.toString());
            document = fallback();
        }
    }

    private static AbyssFloorConfigDocument fallback() {
        AbyssFloorConfigDocument doc = new AbyssFloorConfigDocument();
        doc.setSeasonId("fallback");
        AbyssFloorConfigDocument.Blessing blessing = new AbyssFloorConfigDocument.Blessing();
        blessing.setType("CHARGE_ATTACK");
        blessing.setBonus(0.3);
        doc.setBlessing(blessing);
        for (int f = 1; f <= 3; f++) {
            AbyssFloorConfigDocument.Floor floor = new AbyssFloorConfigDocument.Floor();
            floor.setFloorIndex(f);
            for (int c = 1; c <= 4; c++) {
                AbyssFloorConfigDocument.Chamber chamber = new AbyssFloorConfigDocument.Chamber();
                chamber.setChamberIndex(c);
                chamber.setMonsterGroup("abyss_f" + f + "_c" + c);
                chamber.setTimeLimitSec(180);
                floor.getChambers().add(chamber);
            }
            doc.getFloors().add(floor);
        }
        return doc;
    }
}
