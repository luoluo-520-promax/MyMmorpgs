package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Pity V2 抽卡审计：每次抽卡结果落 Redis Stream（监管可导出），无 Redis 时本地缓冲。
 */
@Service
public class GachaAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(GachaAuditLogService.class);
    public static final String STREAM_KEY = "gacha:audit:stream";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentLinkedQueue<Map<String, String>> local = new ConcurrentLinkedQueue<>();

    public GachaAuditLogService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public void recordDraw(
            long playerId,
            int bannerType,
            int bannerId,
            int itemId,
            int rarity,
            int pity5Before,
            int pity5After,
            int pity4Before,
            int pity4After,
            int failedUpBefore,
            int failedUpAfter,
            boolean isUp,
            boolean softPityTriggered,
            long tsMs) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("playerId", String.valueOf(playerId));
        fields.put("bannerType", String.valueOf(bannerType));
        fields.put("bannerId", String.valueOf(bannerId));
        fields.put("itemId", String.valueOf(itemId));
        fields.put("rarity", String.valueOf(rarity));
        fields.put("pity5Before", String.valueOf(pity5Before));
        fields.put("pity5After", String.valueOf(pity5After));
        fields.put("pity4Before", String.valueOf(pity4Before));
        fields.put("pity4After", String.valueOf(pity4After));
        fields.put("failedUpBefore", String.valueOf(failedUpBefore));
        fields.put("failedUpAfter", String.valueOf(failedUpAfter));
        fields.put("isUp", isUp ? "1" : "0");
        fields.put("softPity", softPityTriggered ? "1" : "0");
        fields.put("algo", "PITY_V2");
        fields.put("ts", String.valueOf(tsMs));
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                MapRecord<String, String, String> record =
                        StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);
                redis.opsForStream().add(record);
                return;
            } catch (Exception e) {
                log.warn("gacha audit stream write failed: {}", e.getMessage());
            }
        }
        if (local.size() > 50_000) {
            local.poll();
        }
        local.offer(fields);
    }

    public List<Map<String, String>> recentLocal(int limit) {
        List<Map<String, String>> all = new ArrayList<>(local);
        int from = Math.max(0, all.size() - Math.max(1, limit));
        return all.subList(from, all.size());
    }

    public Map<String, Object> auditExport(long playerId, int limit) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, String> row : local) {
            if (String.valueOf(playerId).equals(row.get("playerId"))) {
                rows.add(row);
            }
        }
        int capped = Math.min(rows.size(), Math.max(1, limit));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("algo", "PITY_V2");
        out.put("streamKey", STREAM_KEY);
        out.put("count", capped);
        out.put("records", rows.subList(Math.max(0, rows.size() - capped), rows.size()));
        return out;
    }
}
