package cn.itcast.demo.mymmorpg.world.endgame;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 动态词缀：每周从 20 种词缀池抽 5 种，注入肉鸽/深渊怪物配置。
 */
@Service
public class AffixShuffleService {

    public static final int WEEKLY_AFFIX_COUNT = 5;
    public static final int AFFIX_POOL_SIZE = 20;

    private static final List<String> AFFIX_POOL = List.of(
            "POISON_ON_DEATH", "GAUGE_HALVED", "ENEMY_SHIELD_REGEN", "PLAYER_STAMINA_DRAIN",
            "ELITE_SPAWN_BONUS", "TIME_LIMIT_TIGHT", "HEALING_REDUCED", "CRIT_DAMAGE_DOWN",
            "ELEMENT_WEAKNESS_ROTATE", "TRAP_DENSITY_UP", "BOSS_ENRAGE_EARLY", "MINION_SPLIT",
            "FROST_AURA", "FIRE_TRAIL", "LIGHTNING_STRIKE", "VOID_ZONE",
            "LOOT_BONUS", "DAMAGE_REFLECT", "SKILL_CD_UP", "MOVEMENT_SLOW");

    private final ConcurrentHashMap<Long, List<String>> weeklyCache = new ConcurrentHashMap<>();

    public Map<String, Object> weeklyAffixList(long serverId, long nowMs) {
        long week = nowMs / (7L * 86_400_000L);
        List<String> affixes = weeklyCache.computeIfAbsent(week, w -> {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            List<String> pool = new ArrayList<>(AFFIX_POOL);
            List<String> picked = new ArrayList<>();
            for (int i = 0; i < WEEKLY_AFFIX_COUNT && !pool.isEmpty(); i++) {
                int idx = rng.nextInt(pool.size());
                picked.add(pool.remove(idx));
            }
            return List.copyOf(picked);
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("serverId", serverId);
        body.put("weekIndex", week);
        body.put("weeklyAffixList", affixes);
        body.put("poolSize", AFFIX_POOL_SIZE);
        body.put("hint", "每周词缀变化，倒逼调整配队策略");
        return body;
    }

    public double affixModifier(String affixId, String stat) {
        if (affixId == null) {
            return 1.0;
        }
        return switch (affixId) {
            case "BOSS_ENRAGE_EARLY" -> "boss_hp".equals(stat) ? 1.2 : 1.0;
            case "LOOT_BONUS" -> "loot_weight".equals(stat) ? 1.15 : 1.0;
            case "HEALING_REDUCED" -> "healing".equals(stat) ? 0.7 : 1.0;
            default -> 1.0;
        };
    }
}
