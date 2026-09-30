package cn.itcast.demo.mymmorpg.world.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 非战斗 NPC 日常行为 FSM：不依赖 LLM，按游戏时辰切换 IDLE/PATROL/SHOP_OPEN/DIALOGUE。
 */
public final class NpcBehaviorFsm {

    public enum State {
        IDLE,
        PATROL,
        SHOP_OPEN,
        SHOP_CLOSED,
        DIALOGUE
    }

    public record PatrolWaypoint(float x, float y, float z, int dwellSeconds) {
    }

    public record NpcProfile(
            long npcId,
            String name,
            boolean shopNpc,
            int shopOpenHour,
            int shopCloseHour,
            List<PatrolWaypoint> patrolPath) {
    }

    public record NpcRuntime(
            long npcId,
            State state,
            int patrolIndex,
            float x,
            float y,
            float z,
            long stateUntilMs) {
    }

    private final ConcurrentHashMap<Long, NpcProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, NpcRuntime> runtimes = new ConcurrentHashMap<>();

    public void register(NpcProfile profile) {
        profiles.put(profile.npcId(), profile);
        PatrolWaypoint first = profile.patrolPath() == null || profile.patrolPath().isEmpty()
                ? new PatrolWaypoint(0, 0, 0, 0)
                : profile.patrolPath().get(0);
        runtimes.put(profile.npcId(), new NpcRuntime(
                profile.npcId(), State.IDLE, 0, first.x(), first.y(), first.z(), 0L));
    }

    public NpcRuntime tick(long npcId, int hourOfDay, long nowMs) {
        NpcProfile profile = profiles.get(npcId);
        NpcRuntime rt = runtimes.get(npcId);
        if (profile == null || rt == null) {
            return null;
        }
        if (rt.state() == State.DIALOGUE && rt.stateUntilMs() > nowMs) {
            return rt;
        }
        State next;
        if (profile.shopNpc()) {
            next = isShopOpen(hourOfDay, profile.shopOpenHour(), profile.shopCloseHour())
                    ? State.SHOP_OPEN : State.SHOP_CLOSED;
        } else if (profile.patrolPath() != null && !profile.patrolPath().isEmpty()) {
            next = State.PATROL;
        } else {
            next = State.IDLE;
        }
        int patrolIndex = rt.patrolIndex();
        float x = rt.x();
        float y = rt.y();
        float z = rt.z();
        long until = rt.stateUntilMs();
        if (next == State.PATROL) {
            List<PatrolWaypoint> path = profile.patrolPath();
            if (until <= nowMs) {
                patrolIndex = (patrolIndex + 1) % path.size();
                PatrolWaypoint wp = path.get(patrolIndex);
                x = wp.x();
                y = wp.y();
                z = wp.z();
                until = nowMs + Math.max(1, wp.dwellSeconds()) * 1000L;
            }
        }
        NpcRuntime updated = new NpcRuntime(npcId, next, patrolIndex, x, y, z, until);
        runtimes.put(npcId, updated);
        return updated;
    }

    public NpcRuntime startDialogue(long npcId, long durationMs, long nowMs) {
        NpcRuntime rt = runtimes.get(npcId);
        if (rt == null) {
            return null;
        }
        NpcRuntime updated = new NpcRuntime(
                npcId, State.DIALOGUE, rt.patrolIndex(), rt.x(), rt.y(), rt.z(), nowMs + durationMs);
        runtimes.put(npcId, updated);
        return updated;
    }

    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NpcRuntime rt : runtimes.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("npcId", rt.npcId());
            m.put("state", rt.state().name());
            m.put("x", rt.x());
            m.put("y", rt.y());
            m.put("z", rt.z());
            m.put("patrolIndex", rt.patrolIndex());
            out.add(m);
        }
        return out;
    }

    public NpcRuntime get(long npcId) {
        return runtimes.get(npcId);
    }

    private static boolean isShopOpen(int hour, int open, int close) {
        if (open == close) {
            return true;
        }
        if (open < close) {
            return hour >= open && hour < close;
        }
        // 跨夜：如 18-6
        return hour >= open || hour < close;
    }
}
