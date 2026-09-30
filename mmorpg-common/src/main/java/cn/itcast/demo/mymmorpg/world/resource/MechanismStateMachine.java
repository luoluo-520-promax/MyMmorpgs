package cn.itcast.demo.mymmorpg.world.resource;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机关状态机：IDLE → ACTIVE → COOLDOWN → IDLE（或 LOCKED 一次性）。
 */
public final class MechanismStateMachine {

    public enum State {
        IDLE,
        ACTIVE,
        COOLDOWN,
        LOCKED
    }

    public record TransitionResult(boolean ok, State from, State to, String reason) {
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cooldownUntil = new ConcurrentHashMap<>();

    public State get(String mechanismId) {
        return states.getOrDefault(mechanismId, State.IDLE);
    }

    public TransitionResult activate(String mechanismId, boolean oneShot, long cooldownMs, long nowMs) {
        Objects.requireNonNull(mechanismId, "mechanismId");
        State cur = get(mechanismId);
        if (cur == State.LOCKED) {
            return new TransitionResult(false, cur, cur, "locked");
        }
        Long until = cooldownUntil.get(mechanismId);
        if (until != null && until > nowMs) {
            return new TransitionResult(false, cur, cur, "cooldown");
        }
        if (cur == State.ACTIVE) {
            return new TransitionResult(false, cur, cur, "already_active");
        }
        if (oneShot) {
            states.put(mechanismId, State.LOCKED);
            return new TransitionResult(true, cur, State.LOCKED, "one_shot");
        }
        states.put(mechanismId, State.ACTIVE);
        return new TransitionResult(true, cur, State.ACTIVE, "activated");
    }

    public TransitionResult complete(String mechanismId, long cooldownMs, long nowMs) {
        State cur = get(mechanismId);
        if (cur == State.LOCKED) {
            return new TransitionResult(false, cur, cur, "locked");
        }
        if (cur != State.ACTIVE) {
            return new TransitionResult(false, cur, cur, "not_active");
        }
        long cd = Math.max(0L, cooldownMs);
        if (cd > 0) {
            states.put(mechanismId, State.COOLDOWN);
            cooldownUntil.put(mechanismId, nowMs + cd);
            return new TransitionResult(true, cur, State.COOLDOWN, "cooldown");
        }
        states.put(mechanismId, State.IDLE);
        cooldownUntil.remove(mechanismId);
        return new TransitionResult(true, cur, State.IDLE, "idle");
    }

    public void tick(long nowMs) {
        for (Map.Entry<String, Long> e : cooldownUntil.entrySet()) {
            if (e.getValue() <= nowMs) {
                String id = e.getKey();
                cooldownUntil.remove(id, e.getValue());
                states.compute(id, (k, v) -> v == State.COOLDOWN ? State.IDLE : v);
            }
        }
    }

    public Map<String, State> snapshot() {
        return Map.copyOf(states);
    }

    public void reset(String mechanismId) {
        states.remove(mechanismId);
        cooldownUntil.remove(mechanismId);
    }
}
