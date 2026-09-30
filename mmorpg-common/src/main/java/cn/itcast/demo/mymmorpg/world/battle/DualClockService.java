package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双时钟：技能 CD / Buff 计时用墙上时钟（Wall-Clock），动画触发用游戏时间（Game-Time）。
 * 局部子弹时间（0.1x）下 Boss 技能 CD 仍按真实时间缩短，避免慢动作导致技能转好变慢。
 */
@Service
public class DualClockService {

    public static final int CLOCK_WALL = 0;
    public static final int CLOCK_GAME = 1;

    private final ConcurrentHashMap<String, Long> wallCooldownEndMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> gameCooldownEndMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> buffWallEndMs = new ConcurrentHashMap<>();

    /**
     * 注册技能 CD：damageSettlement 走墙上时钟，animationTrigger 走游戏时间。
     */
    public Map<String, Object> startSkillCooldown(
            long entityId, int skillSlot, int cooldownMs,
            double gameTimeScale, long nowMs) {
        String key = skillKey(entityId, skillSlot);
        long wallEnd = nowMs + cooldownMs;
        long scaled = gameTimeScale <= 0 ? cooldownMs
                : (long) Math.max(1, cooldownMs / gameTimeScale);
        long gameEnd = nowMs + scaled;
        wallCooldownEndMs.put(key, wallEnd);
        gameCooldownEndMs.put(key, gameEnd);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("entityId", entityId);
        body.put("skillSlot", skillSlot);
        body.put("wallCooldownEndMs", wallEnd);
        body.put("gameCooldownEndMs", gameEnd);
        body.put("gameTimeScale", gameTimeScale);
        body.put("wallClockForDamage", true);
        body.put("gameClockForAnimation", true);
        return body;
    }

    /**
     * Buff 计时始终用墙上时钟，不受子弹时间影响。
     */
    public Map<String, Object> applyBuff(long entityId, String buffId, int durationMs, long nowMs) {
        String key = buffKey(entityId, buffId);
        long end = nowMs + Math.max(1, durationMs);
        buffWallEndMs.put(key, end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("entityId", entityId);
        body.put("buffId", buffId);
        body.put("wallEndMs", end);
        body.put("clockType", "WALL");
        return body;
    }

    public boolean isSkillReadyWall(long entityId, int skillSlot, long nowMs) {
        Long end = wallCooldownEndMs.get(skillKey(entityId, skillSlot));
        return end == null || nowMs >= end;
    }

    public boolean isSkillReadyGame(long entityId, int skillSlot, long nowMs) {
        Long end = gameCooldownEndMs.get(skillKey(entityId, skillSlot));
        return end == null || nowMs >= end;
    }

    public long skillRemainWallMs(long entityId, int skillSlot, long nowMs) {
        Long end = wallCooldownEndMs.get(skillKey(entityId, skillSlot));
        return end == null ? 0L : Math.max(0L, end - nowMs);
    }

    public long buffRemainWallMs(long entityId, String buffId, long nowMs) {
        Long end = buffWallEndMs.get(buffKey(entityId, buffId));
        return end == null ? 0L : Math.max(0L, end - nowMs);
    }

    /**
     * 子弹时间下：伤害结算用 remainWall，动画播放用 remainGame。
     */
    public Map<String, Object> snapshot(long entityId, int skillSlot, long nowMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("entityId", entityId);
        body.put("skillSlot", skillSlot);
        body.put("wallRemainMs", skillRemainWallMs(entityId, skillSlot, nowMs));
        body.put("gameRemainMs", gameRemainWallMs(entityId, skillSlot, nowMs));
        body.put("skillReadyWall", isSkillReadyWall(entityId, skillSlot, nowMs));
        body.put("skillReadyGame", isSkillReadyGame(entityId, skillSlot, nowMs));
        return body;
    }

    private long gameRemainWallMs(long entityId, int skillSlot, long nowMs) {
        Long end = gameCooldownEndMs.get(skillKey(entityId, skillSlot));
        return end == null ? 0L : Math.max(0L, end - nowMs);
    }

    public void clearEntity(long entityId) {
        String prefix = entityId + ":";
        wallCooldownEndMs.keySet().removeIf(k -> k.startsWith(prefix));
        gameCooldownEndMs.keySet().removeIf(k -> k.startsWith(prefix));
        buffWallEndMs.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static String skillKey(long entityId, int skillSlot) {
        return entityId + ":skill:" + skillSlot;
    }

    private static String buffKey(long entityId, String buffId) {
        return entityId + ":buff:" + (buffId == null ? "" : buffId.trim());
    }
}
