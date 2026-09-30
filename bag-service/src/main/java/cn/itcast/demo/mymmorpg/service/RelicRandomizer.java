package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 圣遗物随机器：主/副词条；支持定向锁定词条不参与随机强化。
 */
@Component
public class RelicRandomizer {

    public static final String[] MAIN_STATS = EquipRandomizer.MAIN_STATS;
    public static final String[] SUB_STATS = EquipRandomizer.SUB_STATS;

    public record RelicRoll(
            String suitId,
            String mainStat,
            double mainValue,
            List<Map<String, Object>> subStats,
            Set<Integer> lockedSubIndexes) {

        public RelicRoll {
            suitId = suitId == null ? "" : suitId;
            mainStat = mainStat == null ? "ATK_PCT" : mainStat;
            subStats = subStats == null ? List.of() : List.copyOf(subStats);
            lockedSubIndexes = lockedSubIndexes == null ? Set.of() : Set.copyOf(lockedSubIndexes);
        }
    }

    public RelicRoll roll(String suitId, int rarity, long seed) {
        Random rng = new Random(seed == 0 ? System.nanoTime() : seed);
        int r = Math.max(1, Math.min(5, rarity <= 0 ? 4 : rarity));
        String main = MAIN_STATS[rng.nextInt(MAIN_STATS.length)];
        double mainValue = 8.0 + r * 3.0 + rng.nextDouble() * 5;
        int subCount = Math.min(4, 2 + r / 2);
        List<Map<String, Object>> subs = new ArrayList<>();
        for (int i = 0; i < subCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stat", SUB_STATS[rng.nextInt(SUB_STATS.length)]);
            row.put("value", Math.round((3.0 + rng.nextDouble() * (2.0 + r)) * 100.0) / 100.0);
            subs.add(row);
        }
        return new RelicRoll(suitId, main, Math.round(mainValue * 100.0) / 100.0, subs, Set.of());
    }

    /**
     * 强化成长：跳过 lockedSubIndexes，在可升级词条池内伪随机。
     */
    public Map<String, Object> growUnlockedSub(RelicRoll roll, long seed) {
        Random rng = new Random(seed);
        List<Map<String, Object>> subs = new ArrayList<>(roll.subStats());
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < subs.size(); i++) {
            if (!roll.lockedSubIndexes().contains(i)) {
                candidates.add(i);
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("lockedIndexes", List.copyOf(roll.lockedSubIndexes()));
        if (candidates.isEmpty()) {
            detail.put("action", "SKIPPED_ALL_LOCKED");
            detail.put("skipped", true);
            return detail;
        }
        if (subs.size() < 4 && candidates.size() == subs.size()) {
            // 未满 4 条：新增一条（不占锁定位）
            String stat = SUB_STATS[rng.nextInt(SUB_STATS.length)];
            double val = Math.round((3.0 + rng.nextDouble() * 4.0) * 100.0) / 100.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stat", stat);
            row.put("value", val);
            subs.add(row);
            detail.put("action", "ADD");
            detail.put("stat", stat);
            detail.put("delta", val);
            detail.put("subStats", subs);
            return detail;
        }
        int idx = candidates.get(rng.nextInt(candidates.size()));
        Map<String, Object> row = new LinkedHashMap<>(subs.get(idx));
        double before = row.get("value") instanceof Number n ? n.doubleValue() : 0;
        double delta = Math.round((1.5 + rng.nextDouble() * 2.5) * 100.0) / 100.0;
        row.put("value", Math.round((before + delta) * 100.0) / 100.0);
        subs.set(idx, row);
        detail.put("action", "UPGRADE");
        detail.put("index", idx);
        detail.put("stat", row.get("stat"));
        detail.put("delta", delta);
        detail.put("before", before);
        detail.put("after", row.get("value"));
        detail.put("subStats", subs);
        detail.put("skippedLocked", true);
        return detail;
    }
}
