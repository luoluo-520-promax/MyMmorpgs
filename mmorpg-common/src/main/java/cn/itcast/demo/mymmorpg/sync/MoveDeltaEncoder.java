package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SceneMoveCmd 位级增量编码：BaseState（首帧全量）+ DeltaState（位掩码字段）。
 * 仅 cameraYaw 变化超过 2° 时才置位，否则服务端用上一帧预测。
 */
@Component
public class MoveDeltaEncoder {

    public static final int MASK_X = 1;
    public static final int MASK_Y = 2;
    public static final int MASK_Z = 4;
    public static final int MASK_SPEED = 8;
    public static final int MASK_MOVEMENT_TYPE = 16;
    public static final int MASK_CAMERA_YAW = 32;
    public static final int MASK_ACTION_STATE = 64;
    public static final int MASK_MOVE_FLAGS = 128;
    public static final int MASK_PHYSICS_HASH = 256;

    public record BaseState(
            float x, float y, float z, float speed,
            MovementType movementType, float cameraYaw,
            ActionState actionState, int moveFlags, String physicsStateHash) {
    }

    public record DeltaState(int changedMask, BaseState values) {
    }

    private final ConcurrentHashMap<Long, BaseState> lastKnown = new ConcurrentHashMap<>();
    private final AtomicLong fullSnapshots = new AtomicLong();
    private final AtomicLong deltasEmitted = new AtomicLong();
    private volatile float positionEpsilon = 0.01f;
    private volatile float yawEpsilonDeg = 2f;

    public void configure(float positionEpsilon, float yawEpsilonDeg) {
        this.positionEpsilon = Math.max(0.001f, positionEpsilon);
        this.yawEpsilonDeg = Math.max(0.5f, yawEpsilonDeg);
    }

    public DeltaState encode(long entityId, SceneMoveCmd cmd) {
        BaseState current = toBaseState(cmd);
        BaseState prev = lastKnown.get(entityId);
        int mask = 0;
        if (prev == null) {
            mask = MASK_X | MASK_Y | MASK_Z | MASK_SPEED | MASK_MOVEMENT_TYPE
                    | MASK_CAMERA_YAW | MASK_ACTION_STATE | MASK_MOVE_FLAGS | MASK_PHYSICS_HASH;
            fullSnapshots.incrementAndGet();
        } else {
            if (Math.abs(prev.x() - current.x()) > positionEpsilon) {
                mask |= MASK_X;
            }
            if (Math.abs(prev.y() - current.y()) > positionEpsilon) {
                mask |= MASK_Y;
            }
            if (Math.abs(prev.z() - current.z()) > positionEpsilon) {
                mask |= MASK_Z;
            }
            if (Math.abs(prev.speed() - current.speed()) > positionEpsilon) {
                mask |= MASK_SPEED;
            }
            if (prev.movementType() != current.movementType()) {
                mask |= MASK_MOVEMENT_TYPE;
            }
            if (yawDelta(prev.cameraYaw(), current.cameraYaw()) > yawEpsilonDeg) {
                mask |= MASK_CAMERA_YAW;
            }
            if (prev.actionState() != current.actionState()) {
                mask |= MASK_ACTION_STATE;
            }
            if (prev.moveFlags() != current.moveFlags()) {
                mask |= MASK_MOVE_FLAGS;
            }
            if (!prev.physicsStateHash().equals(current.physicsStateHash())) {
                mask |= MASK_PHYSICS_HASH;
            }
            if (mask != 0) {
                deltasEmitted.incrementAndGet();
            }
        }
        lastKnown.put(entityId, current);
        return new DeltaState(mask, mergeForWire(prev, current, mask));
    }

    /** 合并预测值：未变化字段沿用上一帧。 */
    public BaseState mergeForWire(BaseState prev, BaseState current, int mask) {
        if (prev == null || mask == 0) {
            return current;
        }
        return new BaseState(
                (mask & MASK_X) != 0 ? current.x() : prev.x(),
                (mask & MASK_Y) != 0 ? current.y() : prev.y(),
                (mask & MASK_Z) != 0 ? current.z() : prev.z(),
                (mask & MASK_SPEED) != 0 ? current.speed() : prev.speed(),
                (mask & MASK_MOVEMENT_TYPE) != 0 ? current.movementType() : prev.movementType(),
                (mask & MASK_CAMERA_YAW) != 0 ? current.cameraYaw() : prev.cameraYaw(),
                (mask & MASK_ACTION_STATE) != 0 ? current.actionState() : prev.actionState(),
                (mask & MASK_MOVE_FLAGS) != 0 ? current.moveFlags() : prev.moveFlags(),
                (mask & MASK_PHYSICS_HASH) != 0 ? current.physicsStateHash() : prev.physicsStateHash());
    }

    public Map<String, Object> packWire(long entityId, DeltaState delta) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entityId", entityId);
        row.put("mask", delta.changedMask());
        BaseState v = delta.values();
        if ((delta.changedMask() & MASK_X) != 0) {
            row.put("x", v.x());
        }
        if ((delta.changedMask() & MASK_Y) != 0) {
            row.put("y", v.y());
        }
        if ((delta.changedMask() & MASK_Z) != 0) {
            row.put("z", v.z());
        }
        if ((delta.changedMask() & MASK_SPEED) != 0) {
            row.put("speed", v.speed());
        }
        if ((delta.changedMask() & MASK_MOVEMENT_TYPE) != 0) {
            row.put("movementType", v.movementType().name());
        }
        if ((delta.changedMask() & MASK_CAMERA_YAW) != 0) {
            row.put("cameraYaw", v.cameraYaw());
        }
        if ((delta.changedMask() & MASK_ACTION_STATE) != 0) {
            row.put("actionState", v.actionState().name());
        }
        if ((delta.changedMask() & MASK_MOVE_FLAGS) != 0) {
            row.put("moveFlags", v.moveFlags());
        }
        if ((delta.changedMask() & MASK_PHYSICS_HASH) != 0) {
            row.put("physicsStateHash", v.physicsStateHash());
        }
        return row;
    }

    public int estimateBytesSaved(DeltaState delta) {
        int fullBytes = 40;
        int sent = 4;
        if ((delta.changedMask() & MASK_X) != 0) {
            sent += 4;
        }
        if ((delta.changedMask() & MASK_Y) != 0) {
            sent += 4;
        }
        if ((delta.changedMask() & MASK_Z) != 0) {
            sent += 4;
        }
        if ((delta.changedMask() & MASK_SPEED) != 0) {
            sent += 4;
        }
        if ((delta.changedMask() & MASK_CAMERA_YAW) != 0) {
            sent += 4;
        }
        if ((delta.changedMask() & MASK_MOVEMENT_TYPE) != 0) {
            sent += 1;
        }
        if ((delta.changedMask() & MASK_ACTION_STATE) != 0) {
            sent += 1;
        }
        if ((delta.changedMask() & MASK_MOVE_FLAGS) != 0) {
            sent += 4;
        }
        return Math.max(0, fullBytes - sent);
    }

    public void evict(long entityId) {
        lastKnown.remove(entityId);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "tracked", lastKnown.size(),
                "fullSnapshots", fullSnapshots.get(),
                "deltasEmitted", deltasEmitted.get(),
                "yawEpsilonDeg", yawEpsilonDeg);
    }

    private static BaseState toBaseState(SceneMoveCmd cmd) {
        return new BaseState(
                cmd.targetX(), cmd.targetY(), cmd.targetZ(), cmd.speed(),
                cmd.movementType(), cmd.cameraYaw(),
                cmd.actionState(), cmd.moveFlags(), cmd.physicsStateHash());
    }

    private static float yawDelta(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }
}
