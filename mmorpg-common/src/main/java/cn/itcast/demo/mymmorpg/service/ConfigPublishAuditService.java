package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 配置发布审计：记录导入 / 热更操作，支持回看最近发布版本（对齐 MyLunarCore ConfigPublishAuditService）。
 */
@Service
public class ConfigPublishAuditService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPublishAuditService.class);

    private final AtomicLong versionSeq = new AtomicLong(1);
    private final CopyOnWriteArrayList<PublishRecord> history = new CopyOnWriteArrayList<>();
    private volatile PublishRecord lastSuccessful;

    public record PublishRecord(
            long version,
            String operator,
            String action,
            List<String> files,
            boolean dryRun,
            boolean success,
            String message,
            Instant at
    ) {
    }

    public PublishRecord record(String operator, String action, List<String> files,
                                boolean dryRun, boolean success, String message) {
        PublishRecord record = new PublishRecord(
                versionSeq.getAndIncrement(),
                operator == null || operator.isBlank() ? "system" : operator,
                action == null ? "" : action,
                files == null ? List.of() : List.copyOf(files),
                dryRun,
                success,
                message == null ? "" : message,
                Instant.now()
        );
        history.add(record);
        while (history.size() > 200) {
            history.remove(0);
        }
        if (success && !dryRun) {
            lastSuccessful = record;
        }
        log.info("Config publish audit: version={}, operator={}, action={}, dryRun={}, success={}, files={}",
                record.version(), record.operator(), record.action(), dryRun, success, record.files());
        return record;
    }

    public PublishRecord lastSuccessful() {
        return lastSuccessful;
    }

    public List<PublishRecord> recent(int limit) {
        if (history.isEmpty()) {
            return List.of();
        }
        int n = Math.max(1, Math.min(limit, history.size()));
        return new ArrayList<>(history.subList(history.size() - n, history.size()));
    }

    public Map<String, Object> toMap(PublishRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", record.version());
        body.put("operator", record.operator());
        body.put("action", record.action());
        body.put("files", record.files());
        body.put("dryRun", record.dryRun());
        body.put("success", record.success());
        body.put("message", record.message());
        body.put("at", record.at().toString());
        return body;
    }
}
