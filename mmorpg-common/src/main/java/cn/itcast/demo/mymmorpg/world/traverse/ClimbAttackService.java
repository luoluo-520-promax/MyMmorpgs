package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攀爬轻攻击：单手持武器，额外扣体力，打断非霸体动作。
 */
@Service
public class ClimbAttackService {

    public static final String EVENT_CLIMB_ATTACK = "CLIMB_ATTACK";
    public static final int EXTRA_STAMINA = 5;

    private final ConcurrentHashMap<Long, Boolean> climbing = new ConcurrentHashMap<>();

    public void setClimbing(long playerId, boolean climbingNow) {
        if (climbingNow) {
            climbing.put(playerId, true);
        } else {
            climbing.remove(playerId);
        }
    }

    public Map<String, Object> climbAttack(
            long playerId, int baseDamage, boolean targetSuperArmor) {
        if (!Boolean.TRUE.equals(climbing.get(playerId))) {
            return Map.of("ok", false, "error", "not_climbing");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", EVENT_CLIMB_ATTACK);
        body.put("damage", Math.max(1, baseDamage));
        body.put("extraStaminaCost", EXTRA_STAMINA);
        body.put("interruptEnemy", !targetSuperArmor);
        body.put("note", "one_hand_weapon_while_climbing");
        return body;
    }
}
