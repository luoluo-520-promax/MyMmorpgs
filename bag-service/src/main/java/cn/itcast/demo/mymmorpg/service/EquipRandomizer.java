package cn.itcast.demo.mymmorpg.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 装备随机词条生成器：主词条 + 多个副词条，序列化为 BLOB 存入 MySQL。
 */
@Component
public class EquipRandomizer {

    public static final String[] MAIN_STATS = {"ATK_PCT", "HP_PCT", "DEF_PCT", "CRIT_RATE", "CRIT_DMG", "ELEM_MASTERY"};
    public static final String[] SUB_STATS = {"ATK_FLAT", "HP_FLAT", "DEF_FLAT", "CRIT_RATE", "CRIT_DMG", "ER", "EM"};

    private final ObjectMapper objectMapper;

    public EquipRandomizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record AffixRoll(String mainStat, double mainValue, List<Map<String, Object>> subStats) {
    }

    public AffixRoll roll(int itemConfigId, int rarity) {
        Random rng = ThreadLocalRandom.current();
        int r = Math.max(1, Math.min(5, rarity <= 0 ? 3 : rarity));
        String main = MAIN_STATS[rng.nextInt(MAIN_STATS.length)];
        double mainValue = switch (main) {
            case "CRIT_RATE" -> 5.0 + r * 2.0 + rng.nextDouble() * 3;
            case "CRIT_DMG" -> 10.0 + r * 4.0 + rng.nextDouble() * 6;
            case "ELEM_MASTERY" -> 20.0 + r * 10 + rng.nextDouble() * 15;
            default -> 8.0 + r * 3.0 + rng.nextDouble() * 5;
        };
        int subCount = Math.min(4, 2 + r / 2);
        List<Map<String, Object>> subs = new ArrayList<>();
        for (int i = 0; i < subCount; i++) {
            String sub = SUB_STATS[rng.nextInt(SUB_STATS.length)];
            double val = 3.0 + rng.nextDouble() * (2.0 + r);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stat", sub);
            row.put("value", Math.round(val * 100.0) / 100.0);
            subs.add(row);
        }
        return new AffixRoll(main, Math.round(mainValue * 100.0) / 100.0, List.copyOf(subs));
    }

    public byte[] toBlob(AffixRoll roll) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("mainStat", roll.mainStat());
        doc.put("mainValue", roll.mainValue());
        doc.put("subStats", roll.subStats());
        try {
            return objectMapper.writeValueAsBytes(doc);
        } catch (JsonProcessingException e) {
            return ("{\"mainStat\":\"" + roll.mainStat() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    public AffixRoll fromBlob(byte[] blob) {
        if (blob == null || blob.length == 0) {
            return new AffixRoll("ATK_PCT", 0, List.of());
        }
        try {
            Map<String, Object> doc = objectMapper.readValue(blob, Map.class);
            String main = String.valueOf(doc.getOrDefault("mainStat", "ATK_PCT"));
            double mainValue = doc.get("mainValue") instanceof Number n ? n.doubleValue() : 0;
            List<Map<String, Object>> subs = doc.get("subStats") instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();
            return new AffixRoll(main, mainValue, subs);
        } catch (Exception e) {
            return new AffixRoll("ATK_PCT", 0, List.of());
        }
    }
}
