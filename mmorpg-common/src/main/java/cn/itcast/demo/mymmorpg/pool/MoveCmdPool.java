package cn.itcast.demo.mymmorpg.pool;

import cn.itcast.demo.mymmorpg.sync.MoveFlags;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 移动指令对象池：复用可变槽位，减少高频 Move 包产生的短生命周期对象与 GC 压力。
 */
@Component
public class MoveCmdPool {

    public static final class Slot {
        float targetX, targetY, targetZ, speed;
        long timestamp;
        MovementType movementType = MovementType.WALK;
        String climbableMeshId = "";
        int moveFlags = MoveFlags.NONE;
        String mountCreatureUid = "";
        String grappleNodeId = "";
        float wallNormalX, wallNormalY, wallNormalZ;
        float inheritedVelocityX, inheritedVelocityY, inheritedVelocityZ;
        String physicsStateHash = "";
        String actionState = "";
        String moveIntent = "";
        float cameraYaw;
        int clientDeltaMs;
        String surfaceType = "PLAIN";

        void reset() {
            targetX = targetY = targetZ = speed = 0f;
            timestamp = 0L;
            movementType = MovementType.WALK;
            climbableMeshId = "";
            moveFlags = MoveFlags.NONE;
            mountCreatureUid = "";
            grappleNodeId = "";
            wallNormalX = wallNormalY = wallNormalZ = 0f;
            inheritedVelocityX = inheritedVelocityY = inheritedVelocityZ = 0f;
            physicsStateHash = "";
            actionState = "";
            moveIntent = "";
            cameraYaw = 0f;
            clientDeltaMs = 16;
            surfaceType = "PLAIN";
        }

        public SceneMoveCmd toImmutable() {
            return new SceneMoveCmd(
                    targetX, targetY, targetZ, speed, timestamp,
                    movementType, climbableMeshId, moveFlags,
                    mountCreatureUid, grappleNodeId,
                    wallNormalX, wallNormalY, wallNormalZ,
                    inheritedVelocityX, inheritedVelocityY, inheritedVelocityZ,
                    physicsStateHash,
                    cn.itcast.demo.mymmorpg.sync.ActionState.fromName(actionState),
                    cn.itcast.demo.mymmorpg.sync.MoveIntent.fromName(moveIntent),
                    cameraYaw, clientDeltaMs,
                    cn.itcast.demo.mymmorpg.sync.SurfaceType.fromName(surfaceType));
        }
    }

    private final ConcurrentLinkedQueue<Slot> pool = new ConcurrentLinkedQueue<>();
    private final AtomicLong acquired = new AtomicLong();
    private final AtomicLong released = new AtomicLong();
    private final AtomicLong created = new AtomicLong();
    private volatile int maxPoolSize = 4096;

    public Slot acquire() {
        Slot slot = pool.poll();
        if (slot == null) {
            slot = new Slot();
            created.incrementAndGet();
        }
        acquired.incrementAndGet();
        return slot;
    }

    public Slot acquireWalk(float x, float y, float z, float speed, long ts) {
        Slot slot = acquire();
        slot.targetX = x;
        slot.targetY = y;
        slot.targetZ = z;
        slot.speed = speed;
        slot.timestamp = ts;
        slot.movementType = MovementType.WALK;
        return slot;
    }

    public void release(Slot slot) {
        if (slot == null) {
            return;
        }
        slot.reset();
        if (pool.size() < maxPoolSize) {
            pool.offer(slot);
        }
        released.incrementAndGet();
    }

    public void configure(int maxPoolSize) {
        this.maxPoolSize = Math.max(256, maxPoolSize);
    }

    public java.util.Map<String, Object> stats() {
        return java.util.Map.of(
                "poolSize", pool.size(),
                "maxPoolSize", maxPoolSize,
                "acquired", acquired.get(),
                "released", released.get(),
                "created", created.get());
    }
}
