package cn.itcast.demo.mymmorpg.world.progression;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能养成建议：装备/圣遗物搭配推荐与一键配置（参考声骸方案分享）。
 */
@Service
public class BuildRecommendationService {

    public record BuildPreset(
            String presetId,
            String characterId,
            String label,
            String weaponId,
            List<String> relicSetIds,
            Map<String, String> mainStats,
            List<String> subStatPriority) {

        public BuildPreset {
            relicSetIds = relicSetIds == null ? List.of() : List.copyOf(relicSetIds);
            mainStats = mainStats == null ? Map.of() : Map.copyOf(mainStats);
            subStatPriority = subStatPriority == null ? List.of() : List.copyOf(subStatPriority);
        }
    }

    private final ConcurrentHashMap<String, BuildPreset> presets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> appliedPreset = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<List<String>>> macroCombos = new ConcurrentHashMap<>();
    public static final int MAX_MACRO_SLOTS = 3;
    public static final long MIN_MACRO_INTERVAL_MS = 2_000L;

    private final ConcurrentHashMap<Long, Long> lastMacroTrigger = new ConcurrentHashMap<>();

    public void register(BuildPreset preset) {
        if (preset != null && preset.presetId() != null) {
            presets.put(preset.presetId(), preset);
        }
    }

    public Map<String, Object> recommend(long playerId, String characterId) {
        List<Map<String, Object>> matches = presets.values().stream()
                .filter(p -> p.characterId().equals(characterId))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("presetId", p.presetId());
                    row.put("label", p.label());
                    row.put("weaponId", p.weaponId());
                    row.put("relicSetIds", p.relicSetIds());
                    row.put("mainStats", p.mainStats());
                    row.put("subStatPriority", p.subStatPriority());
                    row.put("score", scorePreset(p));
                    return row;
                })
                .sorted((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("characterId", characterId);
        body.put("recommendations", matches);
        body.put("topPick", matches.isEmpty() ? null : matches.get(0));
        return body;
    }

    public Map<String, Object> applyPreset(long playerId, String presetId) {
        BuildPreset preset = presets.get(presetId);
        if (preset == null) {
            return Map.of("ok", false, "error", "preset_not_found");
        }
        appliedPreset.put(playerId, presetId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("presetId", presetId);
        body.put("characterId", preset.characterId());
        body.put("equipPlan", Map.of(
                "weaponId", preset.weaponId(),
                "relicSetIds", preset.relicSetIds(),
                "mainStats", preset.mainStats()));
        body.put("oneClickApplied", true);
        body.put("idempotencyKey", "build_apply:" + playerId + ":" + presetId);
        return body;
    }

    public Map<String, Object> saveMacroCombo(long playerId, int slotIndex, List<String> skillSequence, long nowMs) {
        if (slotIndex < 0 || slotIndex >= MAX_MACRO_SLOTS) {
            return Map.of("ok", false, "error", "invalid_slot");
        }
        if (skillSequence == null || skillSequence.isEmpty() || skillSequence.size() > 5) {
            return Map.of("ok", false, "error", "invalid_sequence");
        }
        List<List<String>> slots = macroCombos.computeIfAbsent(playerId, id -> new java.util.ArrayList<>());
        while (slots.size() <= slotIndex) {
            slots.add(List.of());
        }
        slots.set(slotIndex, List.copyOf(skillSequence));
        return Map.of("ok", true, "playerId", playerId, "slotIndex", slotIndex,
                "skillSequence", skillSequence, "minIntervalMs", MIN_MACRO_INTERVAL_MS);
    }

    public Map<String, Object> triggerMacro(long playerId, int slotIndex, long nowMs) {
        Long last = lastMacroTrigger.get(playerId);
        if (last != null && nowMs - last < MIN_MACRO_INTERVAL_MS) {
            return Map.of("ok", false, "error", "macro_rate_limited",
                    "remainMs", MIN_MACRO_INTERVAL_MS - (nowMs - last));
        }
        List<List<String>> slots = macroCombos.get(playerId);
        if (slots == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return Map.of("ok", false, "error", "macro_not_configured");
        }
        List<String> seq = slots.get(slotIndex);
        if (seq == null || seq.isEmpty()) {
            return Map.of("ok", false, "error", "macro_empty");
        }
        lastMacroTrigger.put(playerId, nowMs);
        return Map.of("ok", true, "playerId", playerId, "slotIndex", slotIndex,
                "skillSequence", seq, "serverValidated", true);
    }

    public Map<String, Object> snapshot(long playerId) {
        String presetId = appliedPreset.get(playerId);
        BuildPreset preset = presetId == null ? null : presets.get(presetId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("appliedPresetId", presetId == null ? "" : presetId);
        body.put("appliedPreset", preset == null ? Map.of() : Map.of(
                "label", preset.label(),
                "weaponId", preset.weaponId(),
                "relicSetIds", preset.relicSetIds()));
        body.put("macroCombos", macroCombos.getOrDefault(playerId, List.of()));
        body.put("maxMacroSlots", MAX_MACRO_SLOTS);
        return body;
    }

    private static int scorePreset(BuildPreset p) {
        int score = 50;
        score += p.relicSetIds().size() * 10;
        score += p.mainStats().size() * 5;
        return score;
    }
}
