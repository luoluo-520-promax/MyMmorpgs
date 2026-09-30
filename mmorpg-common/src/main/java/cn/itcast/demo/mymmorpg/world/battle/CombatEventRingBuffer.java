package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 战斗事件环形缓冲区：预分配 DamageEvent / HitResult 槽位，超出上限复用最旧实体，保证零 GC 热路径。
 */
@Component
public class CombatEventRingBuffer {

    public static final int DEFAULT_CAPACITY = 256;
    public static final int MAX_PROJECTILES = 200;

    public static final class HitResult {
        public long attackerId;
        public long targetId;
        public int damage;
        public int verdictCode;
        public long atMs;
        public boolean active;

        public void reset() {
            attackerId = 0L;
            targetId = 0L;
            damage = 0;
            verdictCode = DamageEvent.VERDICT_HIT;
            atMs = 0L;
            active = false;
        }
    }

    private final DamageEvent[] damageSlots;
    private final HitResult[] hitSlots;
    private int damageHead;
    private int hitHead;
    private int damageSize;
    private int hitSize;
    private final AtomicLong damagePublished = new AtomicLong();
    private final AtomicLong hitPublished = new AtomicLong();
    private final AtomicLong projectileCount = new AtomicLong();
    private final AtomicLong projectileRecycled = new AtomicLong();

    public CombatEventRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public CombatEventRingBuffer(int capacity) {
        int cap = Math.max(32, capacity);
        damageSlots = new DamageEvent[cap];
        hitSlots = new HitResult[cap];
        for (int i = 0; i < cap; i++) {
            damageSlots[i] = new DamageEvent();
            hitSlots[i] = new HitResult();
            damageSlots[i].setSlotIndex(i);
        }
    }

    public DamageEvent publishDamage(
            long attackerId, long targetId,
            int rawDamage, int finalDamage,
            int verdictCode, int elementCode,
            int poiseDamage, long atMs) {
        DamageEvent slot = nextDamageSlot();
        slot.populate(attackerId, targetId, rawDamage, finalDamage,
                verdictCode, elementCode, poiseDamage, atMs);
        damagePublished.incrementAndGet();
        return slot;
    }

    public HitResult publishHit(long attackerId, long targetId, int damage, int verdictCode, long atMs) {
        HitResult slot = nextHitSlot();
        slot.attackerId = attackerId;
        slot.targetId = targetId;
        slot.damage = damage;
        slot.verdictCode = verdictCode;
        slot.atMs = atMs;
        slot.active = true;
        hitPublished.incrementAndGet();
        return slot;
    }

    /**
     * 投射物上限 200：超出复用最旧槽位。
     */
    public int acquireProjectileSlot() {
        long count = projectileCount.incrementAndGet();
        if (count > MAX_PROJECTILES) {
            projectileRecycled.incrementAndGet();
            return (int) (count % MAX_PROJECTILES);
        }
        return (int) (count - 1);
    }

    public List<DamageEvent> drainActiveDamage(int max) {
        List<DamageEvent> out = new ArrayList<>(Math.min(max, damageSize));
        int n = 0;
        for (DamageEvent e : damageSlots) {
            if (e.active()) {
                out.add(e);
                n++;
                if (n >= max) {
                    break;
                }
            }
        }
        return out;
    }

    private DamageEvent nextDamageSlot() {
        if (damageSize >= damageSlots.length) {
            DamageEvent oldest = damageSlots[damageHead];
            oldest.reset();
            damageHead = (damageHead + 1) % damageSlots.length;
            return oldest;
        }
        int idx = (damageHead + damageSize) % damageSlots.length;
        damageSize++;
        DamageEvent slot = damageSlots[idx];
        slot.reset();
        return slot;
    }

    private HitResult nextHitSlot() {
        if (hitSize >= hitSlots.length) {
            HitResult oldest = hitSlots[hitHead];
            oldest.reset();
            hitHead = (hitHead + 1) % hitSlots.length;
            return oldest;
        }
        int idx = (hitHead + hitSize) % hitSlots.length;
        hitSize++;
        HitResult slot = hitSlots[idx];
        slot.reset();
        return slot;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capacity", damageSlots.length);
        m.put("damagePublished", damagePublished.get());
        m.put("hitPublished", hitPublished.get());
        m.put("projectileCount", projectileCount.get());
        m.put("projectileRecycled", projectileRecycled.get());
        m.put("maxProjectiles", MAX_PROJECTILES);
        return m;
    }
}
