package cn.itcast.demo.mymmorpg.world.endgame;

import cn.itcast.demo.mymmorpg.world.ecosystem.AffinityService;
import cn.itcast.demo.mymmorpg.world.explore.RegionAwakeningService;
import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import cn.itcast.demo.mymmorpg.world.explore.RegionProgressService;
import cn.itcast.demo.mymmorpg.world.team.TeamCompositionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 肉鸽「命运卡牌」：开局读取大世界探索度/亲密度注入 WorldBuff；通关回写区域潮汐净化。
 */
@Service
public class RogueFateCardService {

    public static final int EXPLORATION_UNLOCK_PERCENT = 60;
    public static final double EXPLORATION_BUFF = 0.08;
    public static final double AFFINITY_RESIST = 0.15;
    public static final int TIDE_PURIFY_DELTA = 25;
    public static final long BUFF_DURATION_MS = 3_600_000L;
    public static final String FATE_ECHO_CURRENCY = "fate_echo";
    public static final double BOSS_AWAKEN_HP_BONUS = 0.20;
    public static final double BOSS_AWAKEN_SPEED_BONUS = 0.10;

    public record FateSession(
            long playerId,
            int rogueId,
            String regionId,
            List<Map<String, Object>> fateCards,
            List<RegionAwakeningService.WorldBuff> worldBuffs,
            long startedAtMs) {
    }

    private final RegionProgressService regionProgress;
    private final AffinityService affinity;
    private final RegionImpactService regions;
    private final ConcurrentHashMap<Long, FateSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> creatureElement = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> fateEchoBalance = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> regionPurifiedThisWeek = new ConcurrentHashMap<>();

    public RogueFateCardService() {
        this(new RegionProgressService(), new AffinityService(), new RegionImpactService());
    }

    public RogueFateCardService(
            RegionProgressService regionProgress,
            AffinityService affinity,
            RegionImpactService regions) {
        this.regionProgress = regionProgress == null ? new RegionProgressService() : regionProgress;
        this.affinity = affinity == null ? new AffinityService() : affinity;
        this.regions = regions == null ? new RegionImpactService() : regions;
        creatureElement.put("eco-fox-1", "CRYO");
        creatureElement.put("crystal_fox", "CRYO");
        creatureElement.put("eco-boar-1", "GEO");
        creatureElement.put("anemo_slime", "ANEMO");
    }

    public void bindCreatureElement(String creatureUid, String element) {
        if (creatureUid != null && element != null) {
            creatureElement.put(creatureUid, element);
        }
    }

    /**
     * {@code POST /internal/rogue/start} 带 region_id：注入与地图状态关联的 WorldBuff。
     */
    public Map<String, Object> startWithRegion(
            long playerId, int rogueId, String regionId, long nowMs) {
        if (regionId == null || regionId.isBlank()) {
            return Map.of("ok", false, "error", "region_id_required");
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        List<RegionAwakeningService.WorldBuff> buffs = new ArrayList<>();

        int percent = explorationPercent(playerId, regionId);
        if (percent >= EXPLORATION_UNLOCK_PERCENT) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("cardId", "fate_explore_" + regionId);
            card.put("type", "EXPLORATION");
            card.put("regionId", regionId);
            card.put("explorationPercent", percent);
            card.put("atkPct", EXPLORATION_BUFF);
            card.put("hint", "区域探索≥60%：额外初始攻击 Buff");
            cards.add(card);
            buffs.add(new RegionAwakeningService.WorldBuff(
                    "rogue-explore:" + regionId, nowMs + BUFF_DURATION_MS, EXPLORATION_BUFF));
        }

        Map.Entry<String, Integer> top = topAffinity(playerId);
        if (top != null && top.getValue() > 0) {
            String element = creatureElement.getOrDefault(top.getKey(), "ANEMO");
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("cardId", "fate_affinity_" + top.getKey());
            card.put("type", "AFFINITY");
            card.put("creatureUid", top.getKey());
            card.put("affinity", top.getValue());
            card.put("element", element);
            card.put("elementResist", AFFINITY_RESIST);
            card.put("hint", "亲密度最高生物赠送对应元素抗性");
            cards.add(card);
            // 抗性以 allAttrBonus 近似写入 WorldBuff，同时返回 elementResist 供战斗侧读取
            buffs.add(new RegionAwakeningService.WorldBuff(
                    "rogue-affinity:" + element, nowMs + BUFF_DURATION_MS, AFFINITY_RESIST * 0.5));
        }

        FateSession session = new FateSession(playerId, rogueId, regionId, cards, buffs, nowMs);
        sessions.put(playerId, session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("rogueId", rogueId);
        body.put("region_id", regionId);
        body.put("fateCards", cards);
        body.put("worldBuffs", buffs.stream().map(b -> Map.of(
                "regionId", b.regionId(),
                "allAttrBonus", b.allAttrBonus(),
                "expiresAtMs", b.expiresAtMs()
        )).toList());
        body.put("mapLinked", true);
        return body;
    }

    /**
     * 通关结算：增加区域潮汐净化值，加速 CHAOS → SAFE。
     */
    public Map<String, Object> settleClear(long playerId, boolean cleared, long nowMs) {
        FateSession session = sessions.get(playerId);
        if (session == null) {
            return Map.of("ok", false, "error", "no_fate_session");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("regionId", session.regionId());
        body.put("cleared", cleared);
        body.put("fateCardsUsed", session.fateCards().size());
        if (cleared) {
            Map<String, Object> purify = regions.addTidePurify(
                    session.regionId(), TIDE_PURIFY_DELTA, nowMs);
            body.put("tidePurify", purify);
            body.put("loopClosed", true);
            body.put("fateEchoEarned", 10);
            fateEchoBalance.merge(playerId, 10, Integer::sum);
            if ("SAFE".equals(String.valueOf(purify.getOrDefault("safety", "")))
                    || Boolean.TRUE.equals(purify.get("converted"))) {
                regionPurifiedThisWeek.put(session.regionId(), true);
                body.put("bossAwakenMode", Map.of(
                        "hpBonus", BOSS_AWAKEN_HP_BONUS,
                        "speedBonus", BOSS_AWAKEN_SPEED_BONUS,
                        "extraReward", "limited_title_card"));
            }
            body.put("hint", "打本治世界：潮汐净化加速区域恢复");
        }
        sessions.remove(playerId);
        return body;
    }

    /** 命运残响兑换圣遗物胚子（主属性定向，副属性仍随机） */
    public Map<String, Object> exchangeFateEcho(
            long playerId, String mainStat, int cost) {
        int balance = fateEchoBalance.getOrDefault(playerId, 0);
        if (balance < cost) {
            return Map.of("ok", false, "error", "insufficient_fate_echo", "balance", balance);
        }
        fateEchoBalance.put(playerId, balance - cost);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("mainStat", mainStat);
        body.put("fateEchoSpent", cost);
        body.put("balance", balance - cost);
        body.put("grantPlan", Map.of(
                "itemId", "relic_blank_" + mainStat.toLowerCase(),
                "count", 1,
                "subStatsRandom", true,
                "idempotencyKey", "fate_echo:" + playerId + ":" + mainStat + ":" + System.nanoTime()));
        return body;
    }

    public boolean isBossAwakenMode(String regionId) {
        return Boolean.TRUE.equals(regionPurifiedThisWeek.get(regionId));
    }

    public FateSession sessionOf(long playerId) {
        return sessions.get(playerId);
    }

    private int explorationPercent(long playerId, String regionId) {
        Map<String, Object> status = regionProgress.status(playerId, regionId);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            return 0;
        }
        Object rp = status.get("region_progress");
        if (rp instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private Map.Entry<String, Integer> topAffinity(long playerId) {
        // AffinityService 无枚举全量 API：探测已知种子生物
        String[] candidates = {"eco-fox-1", "eco-boar-1", "crystal_fox", "anemo_slime"};
        String bestUid = null;
        int best = -1;
        for (String uid : candidates) {
            int a = affinity.get(playerId, uid);
            if (a > best) {
                best = a;
                bestUid = uid;
            }
        }
        if (bestUid == null || best <= 0) {
            return null;
        }
        return Map.entry(bestUid, best);
    }

    /** 供战斗侧读取元素抗性修饰。 */
    public Map<String, Double> elementResistOf(long playerId) {
        FateSession s = sessions.get(playerId);
        Map<String, Double> out = new LinkedHashMap<>();
        if (s == null) {
            return out;
        }
        for (Map<String, Object> card : s.fateCards()) {
            if ("AFFINITY".equals(card.get("type")) && card.get("element") != null) {
                out.put(String.valueOf(card.get("element")), AFFINITY_RESIST);
            }
        }
        return out;
    }

    public TeamCompositionService.Element parseElement(String raw) {
        return TeamCompositionService.Element.parse(raw);
    }
}
