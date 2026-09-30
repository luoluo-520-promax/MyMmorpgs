package cn.itcast.demo.mymmorpg.ai.companion;

import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.ai.SquadCommanderService;
import cn.itcast.demo.mymmorpg.world.explore.ExplorationVitalityService;
import cn.itcast.demo.mymmorpg.world.explore.MapMarkerService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P15 · 常驻 AI 社交伙伴：独立记忆体、战况吐槽指令注入、好感解锁隐藏调查点。
 * <p>
 * 战斗动作仍由 {@link SquadCommanderService} 执行（非 LLM）；指令来源由本服务根据战况生成。
 */
public final class CompanionBotService {

    public static final String REDIS_KEY_PREFIX = "companion:memory:";
    public static final double AFFINITY_SURVEY_THRESHOLD = 0.65;
    public static final double AFFINITY_MARKER_THRESHOLD = 0.80;

    public record MemoryEvent(String type, String summary, long atMs) {
    }

    public record CompanionState(
            long playerId,
            String companionId,
            String name,
            double affinity,
            int fallCount,
            int flowerPicks,
            String combatStyle,
            List<MemoryEvent> history,
            long updatedAtMs) {

        public CompanionState {
            companionId = companionId == null || companionId.isBlank() ? "paimon-like" : companionId.trim();
            name = name == null || name.isBlank() ? "旅伴" : name.trim();
            combatStyle = combatStyle == null || combatStyle.isBlank() ? "BALANCED" : combatStyle.trim();
            history = history == null ? List.of() : List.copyOf(history);
        }
    }

    private final ConcurrentHashMap<Long, MutableMem> memories = new ConcurrentHashMap<>();
    private final AiContentGuard contentGuard;
    private SquadCommanderService squadCommander;
    private ExplorationVitalityService vitality;
    private MapMarkerService mapMarkers;

    public CompanionBotService() {
        this(new AiContentGuard());
    }

    public CompanionBotService(AiContentGuard contentGuard) {
        this.contentGuard = contentGuard == null ? new AiContentGuard() : contentGuard;
    }

    public void bindSquad(SquadCommanderService squad) {
        this.squadCommander = squad;
    }

    public void bindExploration(ExplorationVitalityService vitality, MapMarkerService markers) {
        this.vitality = vitality;
        this.mapMarkers = markers;
    }

    public CompanionState get(long playerId) {
        MutableMem m = memories.computeIfAbsent(playerId, MutableMem::new);
        synchronized (m) {
            return m.snapshot();
        }
    }

    public Map<String, Object> remember(long playerId, String type, String summary) {
        MutableMem m = memories.computeIfAbsent(playerId, MutableMem::new);
        synchronized (m) {
            String t = type == null ? "NOTE" : type.trim().toUpperCase(Locale.ROOT);
            String s = summary == null ? "" : summary.trim();
            m.history.add(new MemoryEvent(t, truncate(s, 120), System.currentTimeMillis()));
            while (m.history.size() > 64) {
                m.history.remove(0);
            }
            if ("FALL".equals(t) || s.contains("坠崖")) {
                m.fallCount++;
                m.affinity = Math.min(1.0, m.affinity + 0.02);
            } else if ("FLOWER".equals(t) || s.contains("花")) {
                m.flowerPicks++;
                m.affinity = Math.min(1.0, m.affinity + 0.03);
            } else if ("COMBAT".equals(t)) {
                if (s.toUpperCase(Locale.ROOT).contains("AGGRESSIVE")) {
                    m.combatStyle = "AGGRESSIVE";
                } else if (s.toUpperCase(Locale.ROOT).contains("CAUTIOUS")) {
                    m.combatStyle = "CAUTIOUS";
                }
                m.affinity = Math.min(1.0, m.affinity + 0.04);
            } else {
                m.affinity = Math.min(1.0, m.affinity + 0.01);
            }
            m.updatedAtMs = System.currentTimeMillis();
            return toMap(m.snapshot());
        }
    }

    /**
     * 伙伴对话气泡：MsgId {@link MessageId#COMPANION_DIALOGUE_SC_NOTIFY} = 2500。
     */
    public Map<String, Object> dialogue(long playerId, String playerLine, String moodHint) {
        CompanionState st = get(playerId);
        String line = craftLine(st, playerLine, moodHint);
        AiContentGuard.GuardResult guard = contentGuard.check(line);
        if (!guard.allowed()) {
            line = guard.text();
        }
        String ttsEmotion = resolveTtsEmotion(st, moodHint);
        Map<String, Object> bubble = new LinkedHashMap<>();
        bubble.put("msgId", MessageId.COMPANION_DIALOGUE_SC_NOTIFY);
        bubble.put("event", "COMPANION_DIALOGUE");
        bubble.put("companionId", st.companionId());
        bubble.put("name", st.name());
        bubble.put("text", line);
        bubble.put("ttsEmotion", ttsEmotion);
        bubble.put("affinity", st.affinity());
        bubble.put("ui", Map.of("bubble", true, "side", "left"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("redisKey", REDIS_KEY_PREFIX + playerId);
        out.put("memory", toMap(st));
        out.put("dialogue", bubble);
        out.put("contentGuard", guard.reason());
        return out;
    }

    /**
     * 根据战况生成小队指令建议（非 LLM 执行层）。
     */
    public Map<String, Object> battleCoach(long playerId, String squadId, Map<String, Object> battlefield) {
        CompanionState st = get(playerId);
        double partyHp = num(battlefield, "partyHpRatio", 1.0);
        double bossHp = num(battlefield, "bossHpRatio", 1.0);
        String advice;
        String command;
        if (partyHp < 0.35) {
            advice = "血量低，建议撤退！我帮你挡这刀——快拉开！";
            command = SquadCommanderService.CMD_SHIELD_WALL;
        } else if (bossHp < 0.25 && "AGGRESSIVE".equals(st.combatStyle())) {
            advice = "Boss 残血了，别怂！齐射收割！";
            command = SquadCommanderService.CMD_ARCHERY_VOLLEY;
        } else if (bossHp < 0.4) {
            advice = "我帮你挡这刀，快输出！";
            command = SquadCommanderService.CMD_FOCUS_FIRE;
        } else if ("CAUTIOUS".equals(st.combatStyle())) {
            advice = "稳一点……先盾墙再找窗口。";
            command = SquadCommanderService.CMD_SHIELD_WALL;
        } else {
            advice = "节奏不错，保持循环，我盯着侧翼。";
            command = SquadCommanderService.CMD_QUICK_MARK;
        }

        Map<String, Object> inject = new LinkedHashMap<>();
        inject.put("suggestedCommand", command);
        inject.put("advice", advice);
        inject.put("companionId", st.companionId());
        inject.put("affinity", st.affinity());
        if (squadCommander != null && squadId != null && !squadId.isBlank()) {
            Map<String, Object> applied = squadCommander.issueCommand(squadId, command);
            inject.put("squadResult", applied);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("inject", inject);
        out.put("dialogue", dialogue(playerId, advice, partyHp < 0.35 ? "URGENT" : "BATTLE").get("dialogue"));
        return out;
    }

    /**
     * 好达标：触发隐藏调查点 / 标出伙伴觉得有趣的地方。
     */
    public Map<String, Object> passiveExplore(long playerId, String regionId, long nowMs) {
        CompanionState st = get(playerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("affinity", st.affinity());
        out.put("surveyUnlocked", st.affinity() >= AFFINITY_SURVEY_THRESHOLD);
        out.put("markerUnlocked", st.affinity() >= AFFINITY_MARKER_THRESHOLD);

        if (st.affinity() >= AFFINITY_SURVEY_THRESHOLD && vitality != null) {
            out.put("hiddenSurvey", vitality.refreshDaily(playerId, regionId == null ? "1" : regionId, nowMs));
        }
        if (st.affinity() >= AFFINITY_MARKER_THRESHOLD && mapMarkers != null) {
            String markerId = "companion-poi-" + playerId + "-" + UUID.randomUUID().toString().substring(0, 8);
            mapMarkers.register(new MapMarkerService.MarkerDef(
                    markerId, MapMarkerService.MarkerKind.LANDMARK,
                    regionId == null ? "1" : regionId,
                    "伙伴觉得有趣的地方", 150f, 2f, 180f));
            out.put("interestingMarkerId", markerId);
            out.put("markers", mapMarkers.markersForRegion(playerId, regionId == null ? "1" : regionId));
        }
        return out;
    }

    public Map<String, Object> toMap(CompanionState st) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playerId", st.playerId());
        m.put("companionId", st.companionId());
        m.put("name", st.name());
        m.put("affinity", st.affinity());
        m.put("fallCount", st.fallCount());
        m.put("flowerPicks", st.flowerPicks());
        m.put("combatStyle", st.combatStyle());
        m.put("updatedAtMs", st.updatedAtMs());
        m.put("redisKey", REDIS_KEY_PREFIX + st.playerId());
        List<Map<String, Object>> recent = new ArrayList<>();
        int from = Math.max(0, st.history().size() - 5);
        for (int i = from; i < st.history().size(); i++) {
            MemoryEvent e = st.history().get(i);
            recent.add(Map.of("type", e.type(), "summary", e.summary(), "atMs", e.atMs()));
        }
        m.put("recentMemories", recent);
        return m;
    }

    private String craftLine(CompanionState st, String playerLine, String moodHint) {
        String mood = moodHint == null ? "" : moodHint.toUpperCase(Locale.ROOT);
        if (mood.contains("URGENT") || mood.contains("LOW_HP")) {
            return "喂！别硬扛啊——先撤！你上次坠崖已经 " + st.fallCount() + " 次了！";
        }
        if (playerLine != null && playerLine.contains("花")) {
            return "又采花？你已经采了 " + st.flowerPicks() + " 次，口味真固定……";
        }
        if (st.affinity() >= 0.8) {
            return "嘿嘿，跟你一起走了这么久，感觉像真正的搭档了。";
        }
        if (st.fallCount() >= 3) {
            return "小心脚下！你的坠崖纪录我可都记着呢。";
        }
        if (playerLine == null || playerLine.isBlank()) {
            return "嗯？怎么了？我在听。";
        }
        return "关于「" + truncate(playerLine, 40) + "」……我觉得可以，但别太莽。";
    }

    private static String resolveTtsEmotion(CompanionState st, String moodHint) {
        if (moodHint != null && moodHint.toUpperCase(Locale.ROOT).contains("URGENT")) {
            return "ANXIOUS";
        }
        if (st.affinity() >= 0.7) {
            return "WARM";
        }
        return "CHEERFUL";
    }

    private static double num(Map<String, Object> m, String key, double def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static final class MutableMem {
        final long playerId;
        String companionId = "paimon-like";
        String name = "旅伴";
        double affinity = 0.2;
        int fallCount;
        int flowerPicks;
        String combatStyle = "BALANCED";
        final List<MemoryEvent> history = new ArrayList<>();
        long updatedAtMs = System.currentTimeMillis();

        MutableMem(long playerId) {
            this.playerId = playerId;
        }

        CompanionState snapshot() {
            return new CompanionState(playerId, companionId, name, affinity, fallCount, flowerPicks,
                    combatStyle, new ArrayList<>(history), updatedAtMs);
        }
    }
}
