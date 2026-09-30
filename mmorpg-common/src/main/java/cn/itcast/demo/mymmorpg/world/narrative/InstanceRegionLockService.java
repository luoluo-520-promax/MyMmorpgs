package cn.itcast.demo.mymmorpg.world.narrative;

import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色故事触发时锁定区域潮汐/刷新，防止 RegionImpact 在任务期间被 tickDecay 改写。
 */
@Service
public class InstanceRegionLockService {

    public record LockRecord(
            long playerId,
            String regionId,
            String storyId,
            RegionImpactService.RegionSafety frozenSafety,
            long lockedAtMs) {
    }

    private final RegionImpactService regions;
    private final ConcurrentHashMap<String, LockRecord> locksByStory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> regionLockCount = new ConcurrentHashMap<>();

    public InstanceRegionLockService() {
        this(new RegionImpactService());
    }

    public InstanceRegionLockService(RegionImpactService regions) {
        this.regions = regions == null ? new RegionImpactService() : regions;
    }

    public RegionImpactService regions() {
        return regions;
    }

    private static String storyKey(long playerId, String storyId) {
        return playerId + ":" + (storyId == null ? "" : storyId.trim());
    }

    public Map<String, Object> lockForStory(long playerId, String regionId, String storyId, long nowMs) {
        if (regionId == null || regionId.isBlank()) {
            return Map.of("ok", false, "error", "region_required");
        }
        Map<String, Object> snap = regions.snapshot(regionId);
        if (!Boolean.TRUE.equals(snap.get("ok"))) {
            return snap;
        }
        RegionImpactService.RegionSafety safety = RegionImpactService.RegionSafety.valueOf(
                String.valueOf(snap.getOrDefault("safety", "HOSTILE")));
        String sk = storyKey(playerId, storyId);
        locksByStory.put(sk, new LockRecord(playerId, regionId, storyId, safety, nowMs));
        regionLockCount.merge(regionId, 1, Integer::sum);
        regions.setTideLocked(regionId, true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("locked", true);
        body.put("regionId", regionId);
        body.put("storyId", storyId);
        body.put("frozenSafety", safety.name());
        body.put("lockCount", regionLockCount.get(regionId));
        return body;
    }

    public Map<String, Object> unlockForStory(long playerId, String storyId) {
        String sk = storyKey(playerId, storyId);
        LockRecord rec = locksByStory.remove(sk);
        if (rec == null) {
            return Map.of("ok", true, "locked", false, "reason", "not_locked");
        }
        int left = regionLockCount.merge(rec.regionId(), -1, Integer::sum);
        if (left <= 0) {
            regionLockCount.remove(rec.regionId());
            regions.setTideLocked(rec.regionId(), false);
            regions.forceSafety(rec.regionId(), rec.frozenSafety());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("unlocked", true);
        body.put("regionId", rec.regionId());
        body.put("restoredSafety", rec.frozenSafety().name());
        body.put("remainingLocks", Math.max(0, left));
        return body;
    }

    public boolean isTideLocked(String regionId) {
        return regionLockCount.getOrDefault(regionId, 0) > 0;
    }

    public LockRecord lockOf(long playerId, String storyId) {
        return locksByStory.get(storyKey(playerId, storyId));
    }
}
