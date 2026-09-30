package cn.itcast.demo.mymmorpg.challenge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 从 classpath JSON 加载挑战关卡配置。 */
@Component
public class ChallengeConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ChallengeConfigLoader.class);
    private static final String RESOURCE = "challenge/challenge_config.json";

    private final ObjectMapper objectMapper;
    private volatile Map<Integer, ChallengeConfigDocument> byId = Map.of();

    public ChallengeConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            List<ChallengeConfigDocument> list = objectMapper.readValue(in, new TypeReference<>() {});
            Map<Integer, ChallengeConfigDocument> map = new HashMap<>();
            if (list != null) {
                for (ChallengeConfigDocument doc : list) {
                    if (doc != null && doc.getId() > 0) {
                        map.put(doc.getId(), doc);
                    }
                }
            }
            byId = Collections.unmodifiableMap(map);
            log.info("已加载 challenge_config {} 条", byId.size());
        } catch (Exception e) {
            log.warn("加载 {} 失败，将使用合成回退配置: {}", RESOURCE, e.toString());
            byId = Map.of();
        }
    }

    public Optional<ChallengeConfigDocument> findById(int challengeId) {
        return Optional.ofNullable(byId.get(challengeId));
    }

    public ChallengeConfigDocument requireOrFallback(int challengeId) {
        return findById(challengeId).orElseGet(() -> ChallengeConfigDocument.fallback(challengeId));
    }
}
