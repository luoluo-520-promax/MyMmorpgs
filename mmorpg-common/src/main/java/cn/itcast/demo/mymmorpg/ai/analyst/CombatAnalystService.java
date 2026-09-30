package cn.itcast.demo.mymmorpg.ai.analyst;

import cn.itcast.demo.mymmorpg.ai.support.AiContentGuard;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * P15 · AI 战斗分析师：规则层统计技能空转 / 闪避浪费 / 元素反应覆盖，
 * 生成层将指标口语化；可选一键跳转强化推荐词条。
 */
public final class CombatAnalystService {

    public record Metrics(
            double skillIdleRate,
            double dodgeWasteRate,
            double reactionCoverage,
            int totalTicks,
            int skillCasts,
            int idleSkillWindows,
            int dodgeAttempts,
            int wastedDodges,
            int reactionHits,
            int reactionWindows,
            List<MistakeMark> mistakes) {

        public Metrics {
            mistakes = mistakes == null ? List.of() : List.copyOf(mistakes);
        }
    }

    public record MistakeMark(int tick, long tsMs, String kind, String detail) {
    }

    public record EquipHint(String slot, String stat, String reason) {
    }

    private final AiContentGuard contentGuard;

    public CombatAnalystService() {
        this(new AiContentGuard());
    }

    public CombatAnalystService(AiContentGuard contentGuard) {
        this.contentGuard = contentGuard == null ? new AiContentGuard() : contentGuard;
    }

    public Metrics analyzeFrames(List<Map<String, Object>> frames) {
        int skillCasts = 0;
        int idleSkillWindows = 0;
        int dodgeAttempts = 0;
        int wastedDodges = 0;
        int reactionHits = 0;
        int reactionWindows = 0;
        int total = frames == null ? 0 : frames.size();
        List<MistakeMark> mistakes = new ArrayList<>();

        if (frames != null) {
            for (Map<String, Object> f : frames) {
                String action = String.valueOf(f.getOrDefault("actionType", "")).toUpperCase(Locale.ROOT);
                int tick = f.get("tick") instanceof Number n ? n.intValue() : 0;
                long ts = f.get("tsMs") instanceof Number n ? n.longValue() : 0L;
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = f.get("payload") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();

                if (action.contains("SKILL") || action.contains("BURST") || action.contains("ULT")) {
                    skillCasts++;
                    double delaySec = num(payload, "castDelaySec", 0);
                    if (delaySec > 0.5) {
                        idleSkillWindows++;
                        mistakes.add(new MistakeMark(tick, ts, "SKILL_IDLE",
                                "技能晚开约 " + String.format(Locale.ROOT, "%.1f", delaySec) + " 秒"));
                    }
                    if (Boolean.TRUE.equals(payload.get("wastedCharge"))
                            || "true".equalsIgnoreCase(String.valueOf(payload.get("wastedCharge")))) {
                        idleSkillWindows++;
                        mistakes.add(new MistakeMark(tick, ts, "SKILL_IDLE", "充能已满却空转"));
                    }
                }
                if (action.contains("DODGE") || action.contains("DASH")) {
                    dodgeAttempts++;
                    boolean hit = Boolean.TRUE.equals(payload.get("avoidedHit"))
                            || "true".equalsIgnoreCase(String.valueOf(payload.get("avoidedHit")));
                    boolean inWindow = Boolean.TRUE.equals(payload.get("inIFrameWindow"))
                            || "true".equalsIgnoreCase(String.valueOf(payload.get("inIFrameWindow")));
                    if (!hit || !inWindow) {
                        wastedDodges++;
                        mistakes.add(new MistakeMark(tick, ts, "DODGE_WASTE", "闪避窗口未吃满"));
                    }
                }
                if (action.contains("REACTION") || action.contains("ELEMENT")) {
                    reactionWindows++;
                    boolean applied = Boolean.TRUE.equals(payload.get("reactionApplied"))
                            || payload.get("reaction") != null;
                    if (applied) {
                        reactionHits++;
                    } else {
                        mistakes.add(new MistakeMark(tick, ts, "REACTION_MISS", "元素反应未吃到"));
                    }
                }
                if (action.contains("HIT_FEEDBACK")) {
                    double latency = num(payload, "latencyMs", 0);
                    if (latency > 120) {
                        mistakes.add(new MistakeMark(tick, ts, "INPUT_LAG",
                                "命中反馈延迟 " + (int) latency + "ms"));
                    }
                }
            }
        }

        double skillIdleRate = skillCasts == 0 ? 0 : (double) idleSkillWindows / skillCasts;
        double dodgeWasteRate = dodgeAttempts == 0 ? 0 : (double) wastedDodges / dodgeAttempts;
        double reactionCoverage = reactionWindows == 0 ? 1.0 : (double) reactionHits / reactionWindows;
        return new Metrics(skillIdleRate, dodgeWasteRate, reactionCoverage, total, skillCasts,
                idleSkillWindows, dodgeAttempts, wastedDodges, reactionHits, reactionWindows, mistakes);
    }

    public String summarize(Metrics m, String personaTone) {
        StringBuilder sb = new StringBuilder();
        if (m.skillIdleRate() >= 0.3) {
            sb.append("你的爆发技能平均每轮晚开了约 ")
                    .append(String.format(Locale.ROOT, "%.1f", estimateDelaySec(m)))
                    .append(" 秒，可能导致少打一次蒸发；试试把充能提到 200%。");
        }
        if (m.dodgeWasteRate() >= 0.4) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("闪避窗口浪费偏高（")
                    .append(percent(m.dodgeWasteRate()))
                    .append("），建议预判红圈前摇再闪，而不是技能后惯性闪。");
        }
        if (m.reactionCoverage() < 0.6) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("元素反应覆盖率仅 ")
                    .append(percent(m.reactionCoverage()))
                    .append("，优先保证挂元素节奏再开爆发。");
        }
        if (sb.length() == 0) {
            sb.append("整体循环干净，技能空转与闪避浪费都在可接受范围，继续保持。");
        }
        if ("HARDCORE".equalsIgnoreCase(personaTone)) {
            sb.append(" 【数据】idle=")
                    .append(percent(m.skillIdleRate()))
                    .append(" dodgeWaste=")
                    .append(percent(m.dodgeWasteRate()))
                    .append(" reaction=")
                    .append(percent(m.reactionCoverage()))
                    .append('.');
        } else if ("CASUAL".equalsIgnoreCase(personaTone)) {
            sb.insert(0, "别着急，慢慢来～ ");
        }
        return sb.toString();
    }

    public List<EquipHint> recommendStats(Metrics m) {
        List<EquipHint> hints = new ArrayList<>();
        if (m.skillIdleRate() >= 0.25) {
            hints.add(new EquipHint("SANDS", "ENERGY_RECHARGE", "充能效率提到约 200% 以减少大招空转"));
        }
        if (m.reactionCoverage() < 0.65) {
            hints.add(new EquipHint("GOBLET", "ELEMENTAL_MASTERY", "提升精通以稳住蒸发/融化覆盖"));
        }
        if (m.dodgeWasteRate() < 0.2 && m.skillIdleRate() < 0.2) {
            hints.add(new EquipHint("CIRCLET", "CRIT_RATE", "循环已稳，可冲暴击期望"));
        }
        return hints;
    }

    /**
     * 基于 {@link BattleReplayService#playback} 结果生成诊断报告。
     */
    public Map<String, Object> feedback(String replayId, Map<String, Object> playback,
                                        String personaTone, boolean applyEnhanceHints) {
        if (playback == null || !Boolean.TRUE.equals(playback.get("ok"))) {
            return Map.of("ok", false, "error",
                    playback == null ? "playback_null" : String.valueOf(playback.get("error")),
                    "replayId", replayId == null ? "" : replayId);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frames = playback.get("frames") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        Metrics metrics = analyzeFrames(frames);
        String advice = summarize(metrics, personaTone);
        AiContentGuard.GuardResult guard = contentGuard.check(advice);
        if (!guard.allowed()) {
            advice = guard.text();
        }
        List<EquipHint> hints = recommendStats(metrics);

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (MistakeMark mk : metrics.mistakes()) {
            timeline.add(Map.of(
                    "tick", mk.tick(),
                    "tsMs", mk.tsMs(),
                    "kind", mk.kind(),
                    "detail", mk.detail(),
                    "highlight", true));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("replayId", replayId);
        out.put("metrics", Map.of(
                "skillIdleRate", round(metrics.skillIdleRate()),
                "dodgeWasteRate", round(metrics.dodgeWasteRate()),
                "reactionCoverage", round(metrics.reactionCoverage()),
                "totalTicks", metrics.totalTicks(),
                "skillCasts", metrics.skillCasts()));
        out.put("advice", advice);
        out.put("mistakeTimeline", timeline);
        out.put("equipHints", hints.stream()
                .map(h -> Map.<String, Object>of("slot", h.slot(), "stat", h.stat(), "reason", h.reason()))
                .toList());
        out.put("contentGuard", guard.reason());
        out.put("personaTone", personaTone == null ? "CASUAL" : personaTone);
        if (applyEnhanceHints) {
            out.put("enhanceDeepLink", Map.of(
                    "service", "EquipEnhanceService",
                    "action", "APPLY_RECOMMENDED_STATS",
                    "hints", out.get("equipHints")));
        }
        out.put("pipeline", "B");
        return out;
    }

    public Map<String, Object> feedbackFromReplay(BattleReplayService replays, String replayId,
                                                  String personaTone, boolean applyEnhanceHints) {
        if (replays == null) {
            return Map.of("ok", false, "error", "replay_service_null");
        }
        Map<String, Object> playback = replays.playback(replayId, 1.0, null, null);
        return feedback(replayId, playback, personaTone, applyEnhanceHints);
    }

    private static double estimateDelaySec(Metrics m) {
        if (m.skillCasts() == 0) {
            return 0;
        }
        return Math.min(2.0, 0.4 + m.skillIdleRate() * 1.2);
    }

    private static String percent(double v) {
        return String.format(Locale.ROOT, "%.0f%%", v * 100);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}
