package cn.itcast.demo.mymmorpg.world.team;

import cn.itcast.demo.mymmorpg.world.explore.RegionAwakeningService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 队伍元素共鸣：根据 4 人队伍元素组合注入战斗期 WorldBuff。
 * <p>语义 API：{@code POST /internal/team/resonance/refresh}（经 scene open-world 暴露）。</p>
 */
@Service
public class TeamCompositionService {

    public enum Element {
        PYRO, HYDRO, ELECTRO, ANEMO, GEO, DENDRO, CRYO;

        public static Element parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String n = raw.trim().toUpperCase(Locale.ROOT);
            return switch (n) {
                case "FIRE", "PYRO" -> PYRO;
                case "WATER", "HYDRO" -> HYDRO;
                case "THUNDER", "LIGHTNING", "ELECTRO" -> ELECTRO;
                case "WIND", "ANEMO" -> ANEMO;
                case "ROCK", "GEO" -> GEO;
                case "GRASS", "DENDRO" -> DENDRO;
                case "ICE", "CRYO" -> CRYO;
                default -> {
                    try {
                        yield Element.valueOf(n);
                    } catch (IllegalArgumentException e) {
                        yield null;
                    }
                }
            };
        }
    }

    public record ResonanceRule(
            Element element,
            int requiredCount,
            String resonanceId,
            String displayName,
            Map<String, Double> attributeModifiers) {
    }

    public record ActiveResonance(
            String resonanceId,
            String displayName,
            Element element,
            int count,
            Map<String, Double> attributeModifiers,
            long expiresAtMs) {
    }

    /** 整场战斗/探索时长（默认 2h，可被 refresh 覆盖）。 */
    public static final long DEFAULT_DURATION_MS = 7_200_000L;

    private final List<ResonanceRule> rules = new ArrayList<>();
    private final ConcurrentHashMap<Long, List<String>> playerTeams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<ActiveResonance>> active = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<RegionAwakeningService.WorldBuff>> battleBuffs =
            new ConcurrentHashMap<>();

    public TeamCompositionService() {
        seedDefaultRules();
    }

    private void seedDefaultRules() {
        rules.add(new ResonanceRule(Element.PYRO, 2, "resonance_pyro_2", "双火共鸣",
                Map.of("atkPct", 0.25)));
        rules.add(new ResonanceRule(Element.PYRO, 4, "resonance_pyro_4", "四火共鸣",
                Map.of("atkPct", 0.40, "critRate", 0.05)));
        rules.add(new ResonanceRule(Element.HYDRO, 2, "resonance_hydro_2", "双水共鸣",
                Map.of("hpPct", 0.25)));
        rules.add(new ResonanceRule(Element.HYDRO, 4, "resonance_hydro_4", "四水共鸣",
                Map.of("hpPct", 0.40, "healingBonus", 0.15)));
        rules.add(new ResonanceRule(Element.ELECTRO, 2, "resonance_electro_2", "双雷共鸣",
                Map.of("energyRecharge", 0.20)));
        rules.add(new ResonanceRule(Element.ANEMO, 2, "resonance_anemo_2", "双风共鸣",
                Map.of("moveSpeedPct", 0.10, "staminaCostReduce", 0.15)));
        rules.add(new ResonanceRule(Element.GEO, 2, "resonance_geo_2", "双岩共鸣",
                Map.of("shieldStrength", 0.15, "defPct", 0.15)));
        rules.add(new ResonanceRule(Element.DENDRO, 2, "resonance_dendro_2", "双草共鸣",
                Map.of("elementalMastery", 50.0)));
        rules.add(new ResonanceRule(Element.CRYO, 2, "resonance_cryo_2", "双冰共鸣",
                Map.of("critRate", 0.15)));
    }

    public void registerRule(ResonanceRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /**
     * 队伍变更时刷新共鸣：写入战斗上下文 WorldBuff，并返回客户端展示字段。
     */
    public Map<String, Object> refresh(
            long playerId, List<String> characterElements, long nowMs, long durationMs) {
        List<String> team = characterElements == null ? List.of() : List.copyOf(characterElements);
        if (team.size() > 4) {
            team = team.subList(0, 4);
        }
        playerTeams.put(playerId, team);

        EnumMap<Element, Integer> counts = new EnumMap<>(Element.class);
        for (String raw : team) {
            Element e = Element.parse(raw);
            if (e != null) {
                counts.merge(e, 1, Integer::sum);
            }
        }

        long expire = nowMs + (durationMs <= 0 ? DEFAULT_DURATION_MS : durationMs);
        List<ActiveResonance> matched = new ArrayList<>();
        Map<String, Double> mergedMods = new LinkedHashMap<>();
        for (ResonanceRule rule : rules) {
            int c = counts.getOrDefault(rule.element(), 0);
            if (c >= rule.requiredCount()) {
                // 同元素取最高档（4 优先于 2）
                matched.removeIf(a -> a.element() == rule.element()
                        && a.count() < rule.requiredCount());
                boolean dominated = matched.stream()
                        .anyMatch(a -> a.element() == rule.element() && a.count() > rule.requiredCount());
                if (dominated) {
                    continue;
                }
                matched.removeIf(a -> a.element() == rule.element());
                matched.add(new ActiveResonance(
                        rule.resonanceId(), rule.displayName(), rule.element(),
                        rule.requiredCount(), rule.attributeModifiers(), expire));
            }
        }
        for (ActiveResonance a : matched) {
            a.attributeModifiers().forEach((k, v) ->
                    mergedMods.merge(k, v, (x, y) -> Math.max(x, y)));
        }
        active.put(playerId, matched);

        List<RegionAwakeningService.WorldBuff> buffs = new ArrayList<>();
        double atk = mergedMods.getOrDefault("atkPct", 0.0);
        double hp = mergedMods.getOrDefault("hpPct", 0.0);
        double allAttr = Math.max(atk, hp) > 0 ? Math.max(atk, hp) * 0.2 : 0.0;
        if (!matched.isEmpty()) {
            buffs.add(new RegionAwakeningService.WorldBuff(
                    "team-resonance:" + playerId, expire, allAttr));
        }
        battleBuffs.put(playerId, buffs);

        String primaryId = matched.isEmpty() ? "" : matched.get(0).resonanceId();
        List<Map<String, Object>> rows = matched.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resonanceId", a.resonanceId());
            row.put("displayName", a.displayName());
            row.put("element", a.element().name());
            row.put("count", a.count());
            row.put("attributeModifiers", a.attributeModifiers());
            row.put("expiresAtMs", a.expiresAtMs());
            return row;
        }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("teamElements", team);
        body.put("resonance_id", primaryId);
        body.put("resonances", rows);
        body.put("attribute_modifiers", mergedMods);
        body.put("worldBuffs", buffs.stream().map(b -> Map.of(
                "regionId", b.regionId(),
                "allAttrBonus", b.allAttrBonus(),
                "expiresAtMs", b.expiresAtMs()
        )).toList());
        body.put("clientHint", matched.isEmpty() ? "无共鸣" : matched.get(0).displayName() + "激活");
        return body;
    }

    public Map<String, Object> refresh(long playerId, List<String> characterElements, long nowMs) {
        return refresh(playerId, characterElements, nowMs, DEFAULT_DURATION_MS);
    }

    public List<ActiveResonance> activeOf(long playerId, long nowMs) {
        List<ActiveResonance> list = active.getOrDefault(playerId, List.of());
        return list.stream().filter(a -> a.expiresAtMs() > nowMs).toList();
    }

    public List<RegionAwakeningService.WorldBuff> battleBuffs(long playerId, long nowMs) {
        return battleBuffs.getOrDefault(playerId, List.of()).stream()
                .filter(b -> b.expiresAtMs() > nowMs)
                .toList();
    }

    public List<String> teamOf(long playerId) {
        return playerTeams.getOrDefault(playerId, List.of());
    }
}
