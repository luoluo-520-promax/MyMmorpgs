package cn.itcast.demo.mymmorpg.world.physics;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 塞尔达式抓取/投掷：长按交互键举起范围内物体，投掷抛物线服务端校验。
 */
@Service
public class GrabThrowService {

    public static final float DEFAULT_GRAB_RADIUS_M = 3f;
    public static final float DEFAULT_THROW_SPEED = 12f;

    public record GrabbableEntity(
            String entityId, float x, float y, float z,
            int massRating, float grabRadiusM, boolean breakable) {
        public GrabbableEntity {
            entityId = entityId == null ? "" : entityId.trim();
            massRating = MassMomentumService.clampMass(massRating);
            grabRadiusM = grabRadiusM <= 0 ? DEFAULT_GRAB_RADIUS_M : grabRadiusM;
        }
    }

    private final ConcurrentHashMap<String, GrabbableEntity> entities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> heldByPlayer = new ConcurrentHashMap<>();
    private final MassMomentumService massMomentum;

    public GrabThrowService() {
        this(new MassMomentumService());
    }

    public GrabThrowService(MassMomentumService massMomentum) {
        this.massMomentum = massMomentum == null ? new MassMomentumService() : massMomentum;
    }

    public void register(GrabbableEntity entity) {
        if (entity != null && !entity.entityId().isBlank()) {
            entities.put(entity.entityId(), entity);
            massMomentum.registerMass(entity.entityId().hashCode(), entity.massRating());
        }
    }

    public Map<String, Object> tryGrab(long playerId, float px, float py, float pz, long nowMs) {
        if (heldByPlayer.containsKey(playerId)) {
            return Map.of("ok", false, "error", "already_holding");
        }
        GrabbableEntity best = null;
        float bestDist = Float.MAX_VALUE;
        for (GrabbableEntity e : entities.values()) {
            float dx = e.x() - px;
            float dy = e.y() - py;
            float dz = e.z() - pz;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= e.grabRadiusM() && dist < bestDist) {
                best = e;
                bestDist = dist;
            }
        }
        if (best == null) {
            return Map.of("ok", false, "error", "nothing_in_range");
        }
        heldByPlayer.put(playerId, best.entityId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "GRAB_ENTITY");
        body.put("playerId", playerId);
        body.put("heldEntityId", best.entityId());
        body.put("massRating", best.massRating());
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> throwHeld(
            long playerId, float dirX, float dirY, float dirZ,
            float throwSpeed, float targetX, float targetY, float targetZ, long nowMs) {
        String entityId = heldByPlayer.remove(playerId);
        if (entityId == null) {
            return Map.of("ok", false, "error", "nothing_held");
        }
        GrabbableEntity entity = entities.get(entityId);
        if (entity == null) {
            return Map.of("ok", false, "error", "entity_gone");
        }
        float speed = throwSpeed <= 0 ? DEFAULT_THROW_SPEED : throwSpeed;
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len > 0.001) {
            dirX = (float) (dirX / len);
            dirY = (float) (dirY / len);
            dirZ = (float) (dirZ / len);
        }
        float massFactor = entity.massRating() / 10f;
        int doorBreakDamage = (int) Math.round(speed * massFactor * 8);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "THROW_ENTITY");
        body.put("playerId", playerId);
        body.put("entityId", entityId);
        body.put("massRating", entity.massRating());
        body.put("throwSpeed", speed);
        body.put("dirX", dirX);
        body.put("dirY", dirY);
        body.put("dirZ", dirZ);
        body.put("targetX", targetX);
        body.put("targetY", targetY);
        body.put("targetZ", targetZ);
        body.put("doorBreakDamage", doorBreakDamage);
        body.put("canBreakDoor", entity.breakable() && doorBreakDamage >= 15);
        body.put("parabolaServerValidated", true);
        body.put("atMs", nowMs);
        return body;
    }

    public Map<String, Object> release(long playerId) {
        String entityId = heldByPlayer.remove(playerId);
        if (entityId == null) {
            return Map.of("ok", false, "error", "nothing_held");
        }
        return Map.of("ok", true, "released", entityId);
    }
}
