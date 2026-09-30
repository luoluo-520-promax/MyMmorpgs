package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 区域世界状态：HOSTILE / CONTESTED / SAFE / CHAOS，含潮汐衰退与动态事件链。
 */
@Service
public class RegionImpactService {

    public enum RegionSafety {
        HOSTILE, CONTESTED, SAFE, CHAOS
    }

    public record RegionProfile(
            String regionId,
            String name,
            int worldId,
            int monsterCampQuota,
            List<String> unlockOnSafe,
            /** SAFE 后若无人维护，多久退回 CONTESTED */
            long decayToContestedMs,
            /** 绑定的世界 Boss Id（SAFE 时可召唤） */
            String boundBossId) {

        public RegionProfile(
                String regionId, String name, int worldId,
                int monsterCampQuota, List<String> unlockOnSafe) {
            this(regionId, name, worldId, monsterCampQuota, unlockOnSafe, 3_600_000L, null);
        }

        public RegionProfile {
            if (regionId == null || regionId.isBlank()) {
                throw new IllegalArgumentException("regionId required");
            }
            name = name == null || name.isBlank() ? regionId : name.trim();
            monsterCampQuota = Math.max(1, monsterCampQuota);
            unlockOnSafe = unlockOnSafe == null ? List.of() : List.copyOf(unlockOnSafe);
            decayToContestedMs = decayToContestedMs <= 0 ? 3_600_000L : decayToContestedMs;
        }
    }

    public record EventChainStep(
            String stepId,
            String description,
            String nextStepId,
            Map<String, Object> payload) {
    }

    /** CHAOS 区域玩家负面：超时叠加诅咒层。 */
    public record PlayerAfflictionConfig(
            long graceMs,
            int maxCurseLayers,
            float atkDownPerLayer,
            float hpDotPerSec,
            boolean disableTeleport) {

        public static PlayerAfflictionConfig defaults() {
            return new PlayerAfflictionConfig(30_000L, 5, 0.05f, 2f, true);
        }

        public PlayerAfflictionConfig {
            graceMs = graceMs <= 0 ? 30_000L : graceMs;
            maxCurseLayers = Math.max(1, maxCurseLayers);
            atkDownPerLayer = atkDownPerLayer <= 0f ? 0.05f : atkDownPerLayer;
            hpDotPerSec = Math.max(0f, hpDotPerSec);
        }
    }

    public record CleanseAnchor(
            String anchorId,
            String regionId,
            float x, float y, float z,
            float radius,
            long cleanseDurationMs) {

        public CleanseAnchor {
            radius = radius <= 0f ? 6f : radius;
            cleanseDurationMs = cleanseDurationMs <= 0 ? 120_000L : cleanseDurationMs;
        }
    }

    public record CurseState(
            int layers,
            long enteredChaosAtMs,
            long lastTickMs,
            long cleansedUntilMs,
            float atkDownPct,
            float hpDotPerSec,
            boolean teleportDisabled) {
    }

    private final ConcurrentHashMap<String, RegionProfile> regions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> remainingCamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegionSafety> safety = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> unlocked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastMaintainedMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> safeSinceMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> anchorActive = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EventChainStep>> eventChains = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeChainStep = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Map<String, Object>>> chainLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CleanseAnchor> cleanseAnchors = new ConcurrentHashMap<>();
    /** playerId:regionId → 进入 CHAOS 时间 / 诅咒 */
    private final ConcurrentHashMap<String, CurseState> playerCurses = new ConcurrentHashMap<>();
    /** 故事实例锁定潮汐：tickDecay 跳过 */
    private final ConcurrentHashMap<String, Boolean> tideLocked = new ConcurrentHashMap<>();
    /** 资源贫瘠：降低采集产出倍率，非伤害 Curse */
    private final ConcurrentHashMap<String, Long> resourceBarrenUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> resourceBarrenMul = new ConcurrentHashMap<>();
    /** 潮汐净化值：累计后可将 CHAOS 推回 SAFE */
    private final ConcurrentHashMap<String, AtomicInteger> tidePurify = new ConcurrentHashMap<>();
    private PlayerAfflictionConfig afflictionConfig = PlayerAfflictionConfig.defaults();
    private Consumer<Map<String, Object>> eventListener;

    public static final int TIDE_PURIFY_THRESHOLD = 100;

    public void register(RegionProfile profile) {
        regions.put(profile.regionId(), profile);
        remainingCamps.put(profile.regionId(), new AtomicInteger(profile.monsterCampQuota()));
        safety.put(profile.regionId(), RegionSafety.HOSTILE);
        unlocked.put(profile.regionId(), new ArrayList<>());
        lastMaintainedMs.put(profile.regionId(), 0L);
        anchorActive.put(profile.regionId(), false);
    }

    public void setEventListener(Consumer<Map<String, Object>> listener) {
        this.eventListener = listener;
    }

    /** 强制覆写区域安全态（拉锯战周结算等）。 */
    public void forceSafety(String regionId, RegionSafety next) {
        if (regionId == null || regionId.isBlank() || next == null) {
            return;
        }
        if (!regions.containsKey(regionId)) {
            return;
        }
        safety.put(regionId, next);
        if (next == RegionSafety.SAFE) {
            lastMaintainedMs.put(regionId, System.currentTimeMillis());
            safeSinceMs.put(regionId, System.currentTimeMillis());
        }
    }

    public RegionSafety safetyOf(String regionId) {
        return safety.getOrDefault(regionId == null ? "" : regionId.trim(), RegionSafety.HOSTILE);
    }

    public void registerEventChain(String regionId, List<EventChainStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        eventChains.put(regionId, List.copyOf(steps));
    }

    /**
     * 清理一座怪物营地；全部清空后区域变 SAFE 并解锁内容，同时启动事件链。
     */
    public Map<String, Object> clearMonsterCamp(String regionId, long playerId) {
        return clearMonsterCamp(regionId, playerId, System.currentTimeMillis());
    }

    public Map<String, Object> clearMonsterCamp(String regionId, long playerId, long nowMs) {
        RegionProfile profile = regions.get(regionId);
        if (profile == null) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        AtomicInteger left = remainingCamps.get(regionId);
        int after = left.decrementAndGet();
        if (after < 0) {
            left.set(0);
            after = 0;
        }
        lastMaintainedMs.put(regionId, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("playerId", playerId);
        body.put("campsRemaining", after);
        if (after == 0) {
            safety.put(regionId, RegionSafety.SAFE);
            safeSinceMs.put(regionId, nowMs);
            List<String> unlocks = new ArrayList<>(profile.unlockOnSafe());
            unlocked.put(regionId, unlocks);
            body.put("safety", RegionSafety.SAFE.name());
            body.put("unlocked", unlocks);
            body.put("worldChanged", true);
            body.put("bossSummonable", profile.boundBossId() != null);
            body.put("boundBossId", profile.boundBossId());
            Map<String, Object> chain = startEventChain(regionId, nowMs);
            body.put("eventChain", chain);
            emit(Map.of("type", "REGION_SAFE", "regionId", regionId, "playerId", playerId));
        } else if (after <= profile.monsterCampQuota() / 2) {
            safety.put(regionId, RegionSafety.CONTESTED);
            body.put("safety", RegionSafety.CONTESTED.name());
            body.put("worldChanged", false);
        } else {
            body.put("safety", RegionSafety.HOSTILE.name());
            body.put("worldChanged", false);
        }
        return body;
    }

    /** 激活锚点：阻止 SAFE 潮汐衰退。 */
    public Map<String, Object> activateAnchor(String regionId, long playerId, long nowMs) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        anchorActive.put(regionId, true);
        lastMaintainedMs.put(regionId, nowMs);
        return Map.of("ok", true, "regionId", regionId, "playerId", playerId, "anchorActive", true);
    }

    public Map<String, Object> enterChaos(String regionId, long nowMs) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        safety.put(regionId, RegionSafety.CHAOS);
        lastMaintainedMs.put(regionId, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("safety", RegionSafety.CHAOS.name());
        body.put("weatherDebuff", Map.of("dotHp", true, "slow", true));
        body.put("affliction", Map.of(
                "graceMs", afflictionConfig.graceMs(),
                "maxCurseLayers", afflictionConfig.maxCurseLayers(),
                "atkDownPerLayer", afflictionConfig.atkDownPerLayer(),
                "hpDotPerSec", afflictionConfig.hpDotPerSec(),
                "disableTeleport", afflictionConfig.disableTeleport()));
        return body;
    }

    public void configureAffliction(PlayerAfflictionConfig config) {
        if (config != null) {
            this.afflictionConfig = config;
        }
    }

    public void registerCleanseAnchor(CleanseAnchor anchor) {
        if (anchor != null && anchor.anchorId() != null) {
            cleanseAnchors.put(anchor.anchorId(), anchor);
        }
    }

    /**
     * 玩家身处 CHAOS：超过 grace 后叠加 CurseLayer（攻降 + DoT），净化窗口内免伤。
     */
    public Map<String, Object> tickPlayerAffliction(
            String regionId, long playerId, long nowMs) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        RegionSafety cur = safety.getOrDefault(regionId, RegionSafety.HOSTILE);
        String key = playerId + ":" + regionId;
        if (cur != RegionSafety.CHAOS) {
            playerCurses.remove(key);
            return Map.of("ok", true, "afflicted", false, "safety", cur.name());
        }
        CurseState state = playerCurses.get(key);
        if (state == null) {
            state = new CurseState(0, nowMs, nowMs, 0L, 0f, 0f, false);
            playerCurses.put(key, state);
        }
        if (state.cleansedUntilMs() > nowMs) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("afflicted", false);
            body.put("cleansed", true);
            body.put("cleansedUntilMs", state.cleansedUntilMs());
            body.put("curseLayers", 0);
            return body;
        }
        long inChaosMs = nowMs - state.enteredChaosAtMs();
        int layers = 0;
        if (inChaosMs >= afflictionConfig.graceMs()) {
            layers = (int) Math.min(afflictionConfig.maxCurseLayers(),
                    1 + (inChaosMs - afflictionConfig.graceMs()) / 15_000L);
        }
        float atkDown = layers * afflictionConfig.atkDownPerLayer();
        float dot = layers * afflictionConfig.hpDotPerSec();
        boolean noTp = layers > 0 && afflictionConfig.disableTeleport();
        CurseState updated = new CurseState(
                layers, state.enteredChaosAtMs(), nowMs, state.cleansedUntilMs(),
                atkDown, dot, noTp);
        playerCurses.put(key, updated);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("afflicted", layers > 0);
        body.put("curseLayers", layers);
        body.put("atkDownPct", atkDown);
        body.put("hpDotPerSec", dot);
        body.put("teleportDisabled", noTp);
        body.put("inChaosMs", inChaosMs);
        body.put("graceMs", afflictionConfig.graceMs());
        return body;
    }

    /** 交互净化锚点：临时抵消 Debuff。 */
    public Map<String, Object> interactCleanseAnchor(
            long playerId, String anchorId, float px, float py, float pz, long nowMs) {
        CleanseAnchor anchor = cleanseAnchors.get(anchorId);
        if (anchor == null) {
            return Map.of("ok", false, "error", "anchor_not_found");
        }
        float dx = px - anchor.x();
        float dy = py - anchor.y();
        float dz = pz - anchor.z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > anchor.radius()) {
            return Map.of("ok", false, "error", "out_of_range");
        }
        String key = playerId + ":" + anchor.regionId();
        CurseState prev = playerCurses.get(key);
        long until = nowMs + anchor.cleanseDurationMs();
        CurseState next = new CurseState(
                0,
                prev == null ? nowMs : prev.enteredChaosAtMs(),
                nowMs,
                until,
                0f, 0f, false);
        playerCurses.put(key, next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("anchorId", anchorId);
        body.put("regionId", anchor.regionId());
        body.put("cleansedUntilMs", until);
        body.put("curseLayers", 0);
        body.put("hint", "净化生效，请继续探索下个锚点");
        emit(Map.of("type", "CLEANSE_ANCHOR", "regionId", anchor.regionId(),
                "playerId", playerId, "anchorId", anchorId));
        return body;
    }

    public CurseState curseOf(long playerId, String regionId) {
        return playerCurses.get(playerId + ":" + regionId);
    }

    public void setTideLocked(String regionId, boolean locked) {
        if (regionId == null || regionId.isBlank()) {
            return;
        }
        if (locked) {
            tideLocked.put(regionId, true);
        } else {
            tideLocked.remove(regionId);
        }
    }

    public boolean isTideLocked(String regionId) {
        return Boolean.TRUE.equals(tideLocked.get(regionId));
    }

    /**
     * 过度采集触发的资源贫瘠 Debuff：降低产出倍率，不造成伤害。
     */
    public Map<String, Object> applyResourceBarren(String regionId, double yieldMul, long untilMs) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        double mul = Math.max(0.1, Math.min(1.0, yieldMul <= 0 ? 0.5 : yieldMul));
        resourceBarrenUntil.put(regionId, untilMs);
        resourceBarrenMul.put(regionId, mul);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("debuff", "RESOURCE_BARREN");
        body.put("yieldMul", mul);
        body.put("untilMs", untilMs);
        body.put("damage", false);
        return body;
    }

    public double gatherYieldMul(String regionId, long nowMs) {
        Long until = resourceBarrenUntil.get(regionId);
        if (until == null || until <= nowMs) {
            resourceBarrenUntil.remove(regionId);
            resourceBarrenMul.remove(regionId);
            return 1.0;
        }
        return resourceBarrenMul.getOrDefault(regionId, 1.0);
    }

    /**
     * 肉鸽通关等来源增加潮汐净化值；达阈值时 CHAOS → SAFE。
     */
    public Map<String, Object> addTidePurify(String regionId, int delta, long nowMs) {
        if (!regions.containsKey(regionId)) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        int next = tidePurify.computeIfAbsent(regionId, id -> new AtomicInteger(0))
                .addAndGet(Math.max(0, delta));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("tidePurify", next);
        body.put("threshold", TIDE_PURIFY_THRESHOLD);
        RegionSafety cur = safety.getOrDefault(regionId, RegionSafety.HOSTILE);
        if (next >= TIDE_PURIFY_THRESHOLD && cur == RegionSafety.CHAOS) {
            safety.put(regionId, RegionSafety.SAFE);
            safeSinceMs.put(regionId, nowMs);
            lastMaintainedMs.put(regionId, nowMs);
            tidePurify.put(regionId, new AtomicInteger(0));
            body.put("safety", RegionSafety.SAFE.name());
            body.put("converted", true);
            emit(Map.of("type", "TIDE_PURIFY_SAFE", "regionId", regionId));
        } else {
            body.put("safety", cur.name());
            body.put("converted", false);
        }
        return body;
    }

    /**
     * 潮汐 tick：SAFE 且无锚点/维护超时 → 退回 CONTESTED；HOSTILE 可叠加负面天气。
     */
    public Map<String, Object> tickDecay(String regionId, long nowMs) {
        RegionProfile profile = regions.get(regionId);
        if (profile == null) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        RegionSafety cur = safety.getOrDefault(regionId, RegionSafety.HOSTILE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("before", cur.name());
        if (isTideLocked(regionId)) {
            body.put("after", cur.name());
            body.put("decayed", false);
            body.put("tideLocked", true);
            return body;
        }
        if (cur == RegionSafety.SAFE
                && !Boolean.TRUE.equals(anchorActive.get(regionId))) {
            long since = safeSinceMs.getOrDefault(regionId, nowMs);
            long last = lastMaintainedMs.getOrDefault(regionId, since);
            long ref = Math.max(since, last);
            if (nowMs - ref >= profile.decayToContestedMs()) {
                safety.put(regionId, RegionSafety.CONTESTED);
                remainingCamps.put(regionId, new AtomicInteger(Math.max(1, profile.monsterCampQuota() / 2)));
                unlocked.put(regionId, new ArrayList<>());
                body.put("after", RegionSafety.CONTESTED.name());
                body.put("decayed", true);
                emit(Map.of("type", "REGION_DECAY", "regionId", regionId));
                return body;
            }
        }
        body.put("after", cur.name());
        body.put("decayed", false);
        body.put("hostileWeather", cur == RegionSafety.HOSTILE || cur == RegionSafety.CHAOS);
        return body;
    }

    /** SAFE 时世界 Boss 可召唤；HOSTILE/CHAOS 时负面天气。 */
    public Map<String, Object> bossLinkStatus(String regionId) {
        RegionProfile profile = regions.get(regionId);
        if (profile == null) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        RegionSafety cur = safety.getOrDefault(regionId, RegionSafety.HOSTILE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("safety", cur.name());
        body.put("boundBossId", profile.boundBossId());
        body.put("bossSummonable", cur == RegionSafety.SAFE && profile.boundBossId() != null);
        body.put("negativeWeather", cur == RegionSafety.HOSTILE || cur == RegionSafety.CHAOS);
        return body;
    }

    public Map<String, Object> advanceEventChain(String regionId, String action, long nowMs) {
        List<EventChainStep> steps = eventChains.get(regionId);
        if (steps == null || steps.isEmpty()) {
            return Map.of("ok", false, "error", "no_event_chain");
        }
        String currentId = activeChainStep.get(regionId);
        EventChainStep current = null;
        for (EventChainStep s : steps) {
            if (s.stepId().equals(currentId)) {
                current = s;
                break;
            }
        }
        if (current == null) {
            return Map.of("ok", false, "error", "chain_not_started");
        }
        String nextId = current.nextStepId();
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("from", current.stepId());
        log.put("action", action);
        log.put("atMs", nowMs);
        if (nextId == null || nextId.isBlank()) {
            activeChainStep.remove(regionId);
            log.put("completed", true);
            chainLog.computeIfAbsent(regionId, id -> new ArrayList<>()).add(log);
            return Map.of("ok", true, "completed", true, "step", current.stepId(), "log", log);
        }
        activeChainStep.put(regionId, nextId);
        log.put("to", nextId);
        log.put("completed", false);
        chainLog.computeIfAbsent(regionId, id -> new ArrayList<>()).add(log);
        EventChainStep next = steps.stream().filter(s -> s.stepId().equals(nextId)).findFirst().orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("completed", false);
        body.put("step", nextId);
        body.put("description", next == null ? "" : next.description());
        body.put("payload", next == null ? Map.of() : next.payload());
        return body;
    }

    public Map<String, Object> snapshot(String regionId) {
        RegionProfile profile = regions.get(regionId);
        if (profile == null) {
            return Map.of("ok", false, "error", "region_not_found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("name", profile.name());
        body.put("safety", safety.getOrDefault(regionId, RegionSafety.HOSTILE).name());
        body.put("campsRemaining", remainingCamps.get(regionId).get());
        body.put("campQuota", profile.monsterCampQuota());
        body.put("unlocked", List.copyOf(unlocked.getOrDefault(regionId, List.of())));
        body.put("anchorActive", Boolean.TRUE.equals(anchorActive.get(regionId)));
        body.put("boundBossId", profile.boundBossId());
        body.put("activeChainStep", activeChainStep.getOrDefault(regionId, ""));
        body.put("decayToContestedMs", profile.decayToContestedMs());
        body.put("tideLocked", isTideLocked(regionId));
        body.put("tidePurify", tidePurify.getOrDefault(regionId, new AtomicInteger(0)).get());
        long barrenUntil = resourceBarrenUntil.getOrDefault(regionId, 0L);
        body.put("resourceBarrenUntilMs", barrenUntil);
        body.put("gatherYieldMul", gatherYieldMul(regionId, System.currentTimeMillis()));
        return body;
    }

    public List<Map<String, Object>> listSnapshots() {
        return regions.keySet().stream().map(this::snapshot).toList();
    }

    /** 公开启动事件链（生态失衡涟漪 / 联机叙事等）。 */
    public Map<String, Object> beginEventChain(String regionId, long nowMs) {
        return startEventChain(regionId, nowMs);
    }

    private Map<String, Object> startEventChain(String regionId, long nowMs) {
        List<EventChainStep> steps = eventChains.get(regionId);
        if (steps == null || steps.isEmpty()) {
            return Map.of("started", false);
        }
        EventChainStep first = steps.get(0);
        activeChainStep.put(regionId, first.stepId());
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("started", true);
        started.put("stepId", first.stepId());
        started.put("description", first.description());
        started.put("payload", first.payload());
        started.put("atMs", nowMs);
        chainLog.computeIfAbsent(regionId, id -> new ArrayList<>()).add(started);
        emit(Map.of("type", "EVENT_CHAIN_START", "regionId", regionId, "stepId", first.stepId()));
        return started;
    }

    private void emit(Map<String, Object> event) {
        if (eventListener != null) {
            eventListener.accept(event);
        }
    }
}
