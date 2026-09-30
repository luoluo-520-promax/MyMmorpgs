package cn.itcast.demo.mymmorpg.world.lock;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 联机实体归属：在单人 SETNX 锁之上叠加「队伍可共享仇恨/结算」。
 * <p>
 * 规则：首个攻击者获得锁；同队伍成员视为共有，可继续输出；异队玩家无法抢锁。
 */
@Component
public class PartyEntityOwnership {

    public enum ThreatPriority {
        /** 攻击者仇恨：最近攻击者优先 */
        ATTACKER,
        /** 房主仇恨：房主始终最高优先级 */
        HOST,
        /** 伤害权重：累计伤害最高者 */
        DAMAGE_WEIGHTED
    }

    public record Ownership(
            int sceneId,
            long entityId,
            long ownerPlayerId,
            long hostPlayerId,
            Set<Long> partyMemberIds,
            ThreatPriority threatPriority,
            DistributedEntityLock.LockHandle lock) {
    }

    private final DistributedEntityLock entityLock;
    private final ConcurrentHashMap<String, Ownership> ownerships = new ConcurrentHashMap<>();

    public PartyEntityOwnership(DistributedEntityLock entityLock) {
        this.entityLock = entityLock == null ? new DistributedEntityLock() : entityLock;
    }

    public PartyEntityOwnership() {
        this(new DistributedEntityLock());
    }

    public Map<String, Object> tryClaim(
            int sceneId, long entityId, long actorPlayerId, long hostPlayerId,
            Set<Long> partyMemberIds, ThreatPriority priority, Duration ttl) {
        String key = sceneId + ":" + entityId;
        Ownership existing = ownerships.get(key);
        if (existing != null) {
            if (existing.partyMemberIds().contains(actorPlayerId) || existing.ownerPlayerId() == actorPlayerId) {
                return view(existing, true, "party_share");
            }
            return Map.of("ok", false, "error", "owned_by_other",
                    "ownerPlayerId", existing.ownerPlayerId());
        }
        DistributedEntityLock.LockHandle handle = entityLock.tryAcquire(sceneId, entityId, actorPlayerId, ttl);
        if (!handle.acquired()) {
            return Map.of("ok", false, "error", "lock_failed");
        }
        Set<Long> party = partyMemberIds == null ? Set.of(actorPlayerId) : Set.copyOf(partyMemberIds);
        Ownership ownership = new Ownership(sceneId, entityId, actorPlayerId, hostPlayerId, party,
                priority == null ? ThreatPriority.ATTACKER : priority, handle);
        ownerships.put(key, ownership);
        return view(ownership, true, "claimed");
    }

    public long resolveThreatTarget(int sceneId, long entityId, long lastAttackerId, Map<Long, Long> damageBoard) {
        Ownership o = ownerships.get(sceneId + ":" + entityId);
        if (o == null) {
            return lastAttackerId;
        }
        return switch (o.threatPriority()) {
            case HOST -> o.hostPlayerId() > 0 ? o.hostPlayerId() : o.ownerPlayerId();
            case DAMAGE_WEIGHTED -> {
                long bestId = o.ownerPlayerId();
                long bestDmg = -1L;
                if (damageBoard != null) {
                    for (Map.Entry<Long, Long> e : damageBoard.entrySet()) {
                        if (o.partyMemberIds().contains(e.getKey()) && e.getValue() > bestDmg) {
                            bestDmg = e.getValue();
                            bestId = e.getKey();
                        }
                    }
                }
                yield bestId;
            }
            case ATTACKER -> lastAttackerId > 0 ? lastAttackerId : o.ownerPlayerId();
        };
    }

    public boolean release(int sceneId, long entityId, long actorPlayerId) {
        String key = sceneId + ":" + entityId;
        Ownership o = ownerships.get(key);
        if (o == null) {
            return false;
        }
        if (o.ownerPlayerId() != actorPlayerId && o.hostPlayerId() != actorPlayerId) {
            return false;
        }
        entityLock.release(o.lock().lockKey(), o.lock().token());
        ownerships.remove(key, o);
        return true;
    }

    public Ownership get(int sceneId, long entityId) {
        return ownerships.get(sceneId + ":" + entityId);
    }

    private static Map<String, Object> view(Ownership o, boolean ok, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("reason", reason);
        m.put("sceneId", o.sceneId());
        m.put("entityId", o.entityId());
        m.put("ownerPlayerId", o.ownerPlayerId());
        m.put("hostPlayerId", o.hostPlayerId());
        m.put("party", o.partyMemberIds());
        m.put("threatPriority", o.threatPriority().name());
        return m;
    }
}
