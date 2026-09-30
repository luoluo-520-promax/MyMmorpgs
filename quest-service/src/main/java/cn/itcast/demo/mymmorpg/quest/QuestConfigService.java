package cn.itcast.demo.mymmorpg.quest;

import cn.itcast.demo.mymmorpg.config.ConfigSnapshotRoot;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务配置：优先读磁盘 {@code config/quest/Quests.json}，否则 classpath，支持 Snapshot Build+Swap。
 */
@Service
public class QuestConfigService {

    private static final Logger log = LoggerFactory.getLogger(QuestConfigService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String configPath;

    public record QuestSnapshot(Map<Integer, QuestTemplate> byId, List<QuestTemplate> all, int version) {
    }

    private final ConfigSnapshotRoot<QuestSnapshot> root =
            new ConfigSnapshotRoot<>(new QuestSnapshot(Collections.emptyMap(), List.of(), 0));

    public QuestConfigService(ObjectMapper objectMapper,
                              ResourceLoader resourceLoader,
                              @Value("${game.quest.config-path:config/quest/Quests.json}") String configPath) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.configPath = configPath;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    /**
     * Build 完整快照并原子 Swap；任一校验失败则指针不动。
     */
    public boolean reload() {
        try {
            QuestSnapshot built = buildSnapshot();
            if (built.byId().isEmpty()) {
                log.warn("Quest config empty at {}, keep previous snapshot (size={})",
                        configPath, root.get().byId().size());
                return !root.get().byId().isEmpty();
            }
            root.swap(built);
            log.info("Quest configs loaded: total={}, version={}, path={}",
                    built.byId().size(), built.version(), configPath);
            return true;
        } catch (Exception e) {
            log.error("Quest config reload failed, keep previous snapshot", e);
            return false;
        }
    }

    /** 仅 Build，不 Swap；供 Admin 两阶段热更 prepare 使用。 */
    public QuestSnapshot prepare() throws Exception {
        QuestSnapshot built = buildSnapshot();
        if (built.byId().isEmpty()) {
            throw new IllegalStateException("quest config empty, refuse prepare");
        }
        return built;
    }

    public void swapPrepared(QuestSnapshot snapshot) {
        root.swap(snapshot);
    }

    private QuestSnapshot buildSnapshot() throws Exception {
        QuestsFile file = readFile();
        Map<Integer, QuestTemplate> index = new LinkedHashMap<>();
        List<QuestTemplate> list = new ArrayList<>();
        if (file != null && file.quests != null) {
            for (QuestTemplate tpl : file.quests) {
                if (tpl == null || tpl.questId <= 0) {
                    continue;
                }
                if (tpl.target <= 0) {
                    tpl.target = 1;
                }
                if (tpl.name == null) {
                    tpl.name = "";
                }
                if (tpl.description == null) {
                    tpl.description = "";
                }
                index.put(tpl.questId, tpl);
                list.add(tpl);
            }
        }
        int nextVersion = root.get().version() + 1;
        return new QuestSnapshot(Collections.unmodifiableMap(index), List.copyOf(list), nextVersion);
    }

    public QuestTemplate find(int questId) {
        return root.get().byId().get(questId);
    }

    public List<QuestTemplate> listAll() {
        return root.get().all();
    }

    public int version() {
        return root.get().version();
    }

    /**
     * Admin 导入覆盖内存配置（不强制写盘）；校验失败返回 false，不切换根指针。
     */
    public boolean applyQuests(List<QuestTemplate> incoming) {
        List<String> errors = QuestImportValidator.validate(incoming);
        if (!errors.isEmpty()) {
            log.warn("Quest apply rejected: {}", errors);
            return false;
        }
        Map<Integer, QuestTemplate> index = new LinkedHashMap<>();
        List<QuestTemplate> list = new ArrayList<>();
        for (QuestTemplate tpl : incoming) {
            if (tpl.target <= 0) {
                tpl.target = 1;
            }
            if (tpl.name == null) {
                tpl.name = "";
            }
            if (tpl.description == null) {
                tpl.description = "";
            }
            index.put(tpl.questId, tpl);
            list.add(tpl);
        }
        int nextVersion = root.get().version() + 1;
        root.swap(new QuestSnapshot(Collections.unmodifiableMap(index), List.copyOf(list), nextVersion));
        log.info("Quest configs applied: total={}, version={}", index.size(), nextVersion);
        return true;
    }

    private QuestsFile readFile() throws Exception {
        Path path = Path.of(configPath);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                return objectMapper.readValue(in, QuestsFile.class);
            }
        }
        Resource resource = resourceLoader.getResource("classpath:" + configPath);
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, QuestsFile.class);
            }
        }
        Path alt = Path.of("config/quest/Quests.json");
        if (Files.isRegularFile(alt)) {
            try (InputStream in = Files.newInputStream(alt)) {
                return objectMapper.readValue(in, QuestsFile.class);
            }
        }
        log.warn("Quests.json not found at {} or classpath", configPath);
        return new QuestsFile();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestsFile {
        public List<QuestTemplate> quests = new ArrayList<>();
    }
}
