package cn.itcast.demo.mymmorpg.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 玩家移动轨迹写入 Redis Stream，异步聚合后再回写 MySQL，避免位置更新打爆数据库。
 */
@Component
public class PositionStreamWriter {

    private static final Logger log = LoggerFactory.getLogger(PositionStreamWriter.class);
    public static final String STREAM_KEY = "world:position:stream";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ConcurrentLinkedQueue<Map<String, String>> localBuffer = new ConcurrentLinkedQueue<>();
    private static final int LOCAL_CAP = 10_000;

    public PositionStreamWriter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public PositionStreamWriter() {
        this.redisProvider = null;
    }

    public boolean append(long playerId, int sceneId, int lineId, float x, float y, float z, long tsMs) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("playerId", String.valueOf(playerId));
        fields.put("sceneId", String.valueOf(sceneId));
        fields.put("lineId", String.valueOf(lineId));
        fields.put("x", Float.toString(x));
        fields.put("y", Float.toString(y));
        fields.put("z", Float.toString(z));
        fields.put("ts", String.valueOf(tsMs));
        StringRedisTemplate redis = redis();
        if (redis != null) {
            try {
                MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);
                redis.opsForStream().add(record);
                return true;
            } catch (Exception e) {
                log.warn("position stream append failed, buffer local: {}", e.getMessage());
            }
        }
        if (localBuffer.size() >= LOCAL_CAP) {
            localBuffer.poll();
        }
        localBuffer.offer(fields);
        return false;
    }

    /** 聚合本地缓冲：每玩家取最新一条，供定时刷 MySQL。 */
    public List<Map<String, String>> drainLatestPerPlayer(int max) {
        Map<Long, Map<String, String>> latest = new LinkedHashMap<>();
        Map<String, String> item;
        int n = 0;
        while (n < Math.max(1, max) * 4 && (item = localBuffer.poll()) != null) {
            n++;
            long pid = Long.parseLong(item.getOrDefault("playerId", "0"));
            if (pid > 0) {
                latest.put(pid, item);
            }
        }
        return new ArrayList<>(latest.values());
    }

    public int localBufferSize() {
        return localBuffer.size();
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
