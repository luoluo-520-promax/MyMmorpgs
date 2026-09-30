package cn.itcast.demo.mymmorpg.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 业务大盘指标：在线房间、世界 Boss、抽卡流水、体力药剂等（进程内聚合，可挂 Prometheus）。
 */
public final class BusinessMetricsDashboard {

    private final AtomicLong onlineRooms = new AtomicLong();
    private final AtomicLong worldBossAlive = new AtomicLong();
    private final ConcurrentHashMap<String, Long> worldBossRespawnAt = new ConcurrentHashMap<>();
    private final LongAdder gachaUpPoolRevenue = new LongAdder();
    private final LongAdder resinPotionConsumed = new LongAdder();
    private final LongAdder portalPreloads = new LongAdder();
    private final LongAdder reconnectProtections = new LongAdder();
    private final LongAdder worldSharedCollects = new LongAdder();

    public void setOnlineRooms(long n) {
        onlineRooms.set(Math.max(0L, n));
    }

    public void setWorldBossAlive(long n) {
        worldBossAlive.set(Math.max(0L, n));
    }

    public void setBossRespawnAt(String bossId, long respawnAtMs) {
        if (bossId != null) {
            worldBossRespawnAt.put(bossId, respawnAtMs);
        }
    }

    public void clearBossRespawn(String bossId) {
        if (bossId != null) {
            worldBossRespawnAt.remove(bossId);
        }
    }

    public void addGachaRevenue(long amount) {
        if (amount > 0) {
            gachaUpPoolRevenue.add(amount);
        }
    }

    public void addResinPotion(long count) {
        if (count > 0) {
            resinPotionConsumed.add(count);
        }
    }

    public void incPortalPreload() {
        portalPreloads.increment();
    }

    public void incReconnectProtection() {
        reconnectProtections.increment();
    }

    public void incWorldSharedCollect() {
        worldSharedCollects.increment();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("onlineRooms", onlineRooms.get());
        m.put("worldBossAlive", worldBossAlive.get());
        m.put("worldBossRespawnAt", Map.copyOf(worldBossRespawnAt));
        m.put("gachaUpPoolRevenue", gachaUpPoolRevenue.sum());
        m.put("resinPotionConsumed", resinPotionConsumed.sum());
        m.put("portalPreloads", portalPreloads.sum());
        m.put("reconnectProtections", reconnectProtections.sum());
        m.put("worldSharedCollects", worldSharedCollects.sum());
        return m;
    }
}
