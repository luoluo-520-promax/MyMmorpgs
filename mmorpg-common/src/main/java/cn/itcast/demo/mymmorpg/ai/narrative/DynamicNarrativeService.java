package cn.itcast.demo.mymmorpg.ai.narrative;

import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.world.puzzle.RuleTriggerService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * P15 · 世界情境叙事引擎：将 WorldState / 编年史 / NPC 好感打包为 Structured World Snapshot，
 * 经「情境压缩」生成对话意图 JSON，再渲染带情感标签的台词（不生成游戏逻辑）。
 */
public final class DynamicNarrativeService {

    public static final Set<String> ALLOWED_INTENTS = Set.of(
            "GREET", "COMFORT", "TEASE", "WARN", "CELEBRATE", "MOURN", "HINT_LORE", "REACT_ITEM");

    public record WorldSnapshot(
            long playerId,
            String npcId,
            String weather,
            String epoch,
            String regionTide,
            String triggerEventId,
            List<String> chronicleFlags,
            double npcTrust,
            double npcFear,
            List<String> specialBagItems,
            Map<String, Object> extras) {

        public WorldSnapshot {
            weather = blankTo(weather, "CLEAR");
            epoch = blankTo(epoch, "PEACE");
            regionTide = blankTo(regionTide, "CALM");
            triggerEventId = blankTo(triggerEventId, "IDLE");
            chronicleFlags = chronicleFlags == null ? List.of() : List.copyOf(chronicleFlags);
            specialBagItems = specialBagItems == null ? List.of() : List.copyOf(specialBagItems);
            extras = extras == null ? Map.of() : Map.copyOf(extras);
            npcId = blankTo(npcId, "guide");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("playerId", playerId);
            m.put("npcId", npcId);
            m.put("weather", weather);
            m.put("epoch", epoch);
            m.put("regionTide", regionTide);
            m.put("triggerEventId", triggerEventId);
            m.put("chronicleFlags", chronicleFlags);
            m.put("npcTrust", npcTrust);
            m.put("npcFear", npcFear);
            m.put("specialBagItems", specialBagItems);
            m.put("extras", extras);
            return m;
        }
    }

    public record NarrativeIntent(String intent, String emotion, String hint, boolean questSafe) {
        public NarrativeIntent {
            intent = intent == null ? "GREET" : intent.trim().toUpperCase(Locale.ROOT);
            emotion = emotion == null ? "NEUTRAL" : emotion.trim().toUpperCase(Locale.ROOT);
            hint = hint == null ? "" : hint.trim();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("intent", intent);
            m.put("emotion", emotion);
            m.put("hint", hint);
            m.put("questSafe", questSafe);
            return m;
        }
    }

    private final AiContentGuard contentGuard;
    private final RuleTriggerService ruleTriggers;

    public DynamicNarrativeService() {
        this(new AiContentGuard(), new RuleTriggerService());
    }

    public DynamicNarrativeService(AiContentGuard contentGuard, RuleTriggerService ruleTriggers) {
        this.contentGuard = contentGuard == null ? new AiContentGuard() : contentGuard;
        this.ruleTriggers = ruleTriggers == null ? new RuleTriggerService() : ruleTriggers;
    }

    /**
     * 情境压缩：规则层从 Snapshot 推导意图（可替换为真实 LLM JSON 输出）。
     */
    public NarrativeIntent compress(WorldSnapshot snap) {
        if (snap == null) {
            return new NarrativeIntent("GREET", "NEUTRAL", "欢迎旅人", true);
        }
        String ev = snap.triggerEventId().toUpperCase(Locale.ROOT);
        if (ev.contains("BOSS") || ev.contains("DRAGON") || snap.chronicleFlags().stream()
                .anyMatch(f -> f.toUpperCase(Locale.ROOT).contains("BOSS_DEFEAT"))) {
            return new NarrativeIntent("MOURN", "SORROW", "提及刚摧毁的巨龙尸体", true);
        }
        if (ev.contains("STORM") || "STORM".equalsIgnoreCase(snap.weather())
                || "CHAOS".equalsIgnoreCase(snap.regionTide())) {
            return new NarrativeIntent("WARN", "FEAR", "天气突变，劝玩家寻找掩体", true);
        }
        if (!snap.specialBagItems().isEmpty()) {
            String item = snap.specialBagItems().get(0);
            return new NarrativeIntent("REACT_ITEM", "CURIOSITY", "注意到背包中的「" + item + "」", true);
        }
        if (snap.npcTrust() >= 0.7) {
            return new NarrativeIntent("COMFORT", "WARM", "以老友口吻关心旅途", true);
        }
        if (snap.npcFear() >= 0.5) {
            return new NarrativeIntent("WARN", "TENSE", "语气戒备，提及区域潮汐不稳", true);
        }
        if ("WAR".equalsIgnoreCase(snap.epoch())) {
            return new NarrativeIntent("HINT_LORE", "SOLEMN", "纪元战火未熄，点到为止", true);
        }
        return new NarrativeIntent("GREET", "NEUTRAL", "日常寒暄", true);
    }

    public String renderLine(NarrativeIntent intent, WorldSnapshot snap) {
        String hint = intent.hint().isBlank() ? "此刻的世界" : intent.hint();
        return switch (intent.intent()) {
            case "COMFORT" -> "……你看起来累了。坐下歇歇吧——关于" + hint + "，我想听听你的感受。";
            case "TEASE" -> "哟，又是你。关于" + hint + "，可别指望我再夸你一次。";
            case "WARN" -> "小心！我能感觉到" + hint + "——别硬闯。";
            case "CELEBRATE" -> "干得漂亮！连" + hint + "都被你改写了。";
            case "MOURN" -> "空气里还残着硫磺味……" + hint + "。胜利不总是轻松的。";
            case "HINT_LORE" -> "有件事我想告诉你：" + hint + "。别声张。";
            case "REACT_ITEM" -> "等等——你身上带着" + hint + "？这东西可不能随便亮出来。";
            default -> "旅人，今日" + blankTo(snap == null ? null : snap.weather(), "天气")
                    + "尚可。" + (hint.isBlank() ? "" : "（" + hint + "）");
        };
    }

    /**
     * 全链路：压缩 → 敏感词校验 → RuleTrigger 隐任务校验 → 渲染台词。
     */
    public Map<String, Object> generate(WorldSnapshot snap) {
        NarrativeIntent intent = compress(snap);
        if (!ALLOWED_INTENTS.contains(intent.intent())) {
            intent = new NarrativeIntent("GREET", "NEUTRAL", "意图非法已回退", true);
        }

        String line = renderLine(intent, snap);
        AiContentGuard.GuardResult guard = contentGuard.check(line);
        if (!guard.allowed()) {
            line = guard.text();
            intent = new NarrativeIntent(intent.intent(), "NEUTRAL", intent.hint(), true);
        }

        Map<String, Object> ruleCtx = new LinkedHashMap<>();
        ruleCtx.put("source", "ai_narrative");
        ruleCtx.put("intent", intent.intent());
        ruleCtx.put("npcId", snap == null ? "guide" : snap.npcId());
        ruleCtx.put("triggerEventId", snap == null ? "IDLE" : snap.triggerEventId());
        ruleCtx.put("playerId", snap == null ? 0L : snap.playerId());
        Map<String, Object> ruleResult = ruleTriggers.fire("AI_NARRATIVE", ruleCtx);
        boolean questLeak = hasQuestGrantAction(ruleResult);
        if (questLeak) {
            intent = new NarrativeIntent(intent.intent(), intent.emotion(),
                    intent.hint() + "（已剥离未授权任务发放）", true);
            line = renderLine(intent, snap);
            ruleResult = Map.of("ok", true, "matched", List.of(), "blockedQuestGrant", true);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", snap == null ? 0L : snap.playerId());
        out.put("npcId", snap == null ? "guide" : snap.npcId());
        out.put("triggerEventId", snap == null ? "IDLE" : snap.triggerEventId());
        out.put("snapshot", snap == null ? Map.of() : snap.toMap());
        out.put("intent", intent.toMap());
        out.put("dialogue", line);
        out.put("emotionTag", intent.emotion());
        out.put("contentGuard", guard.reason());
        out.put("ruleCheck", ruleResult);
        out.put("pipeline", "B");
        return out;
    }

    public Map<String, Object> generateFromRequest(Map<String, Object> body) {
        return generate(parseSnapshot(body));
    }

    public static WorldSnapshot parseSnapshot(Map<String, Object> body) {
        if (body == null) {
            return new WorldSnapshot(0, "guide", "CLEAR", "PEACE", "CALM", "IDLE",
                    List.of(), 0.3, 0.1, List.of(), Map.of());
        }
        long playerId = num(body, "playerId", 0);
        String npcId = str(body, "npcId", "guide");
        String weather = str(body, "weather", "CLEAR");
        String epoch = str(body, "epoch", "PEACE");
        String regionTide = str(body, "regionTide", "CALM");
        String triggerEventId = str(body, "triggerEventId", "IDLE");
        List<String> flags = stringList(body.get("chronicleFlags"));
        List<String> items = stringList(body.get("specialBagItems"));
        double trust = num(body, "npcTrust", 0.3);
        double fear = num(body, "npcFear", 0.1);
        @SuppressWarnings("unchecked")
        Map<String, Object> extras = body.get("extras") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = body.get("snapshot") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        if (nested != null) {
            return parseSnapshot(nested);
        }
        return new WorldSnapshot(playerId, npcId, weather, epoch, regionTide, triggerEventId,
                flags, trust, fear, items, extras);
    }

    private static boolean hasQuestGrantAction(Map<String, Object> ruleResult) {
        Object matched = ruleResult == null ? null : ruleResult.get("matched");
        if (!(matched instanceof List<?> list)) {
            return false;
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> hit)) {
                continue;
            }
            Object actions = hit.get("actions");
            if (!(actions instanceof List<?> acts)) {
                continue;
            }
            for (Object a : acts) {
                if (a instanceof Map<?, ?> am) {
                    Object typeObj = am.get("type");
                    String type = typeObj == null ? "" : String.valueOf(typeObj);
                    if (type.contains("QUEST") || type.contains("GRANT_QUEST")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && !String.valueOf(o).isBlank()) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static long num(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return def;
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }
}
