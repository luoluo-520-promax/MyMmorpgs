package cn.itcast.demo.mymmorpg.world.battle;

/**
 * 战斗伤害事件（零 GC 路径）：使用 int 状态码代替 Enum，配合 {@link CombatEventRingBuffer} 预分配复用。
 */
public final class DamageEvent {

    public static final int VERDICT_HIT = 0;
    public static final int VERDICT_MISS = 1;
    public static final int VERDICT_SOFT_ROLLBACK = 2;

    public static final int ELEMENT_NONE = 0;
    public static final int ELEMENT_PYRO = 1;
    public static final int ELEMENT_HYDRO = 2;
    public static final int ELEMENT_ELECTRO = 3;

    private long attackerId;
    private long targetId;
    private int rawDamage;
    private int finalDamage;
    private int verdictCode;
    private int elementCode;
    private int poiseDamage;
    private long atMs;
    private int slotIndex = -1;
    private boolean active;

    public void reset() {
        attackerId = 0L;
        targetId = 0L;
        rawDamage = 0;
        finalDamage = 0;
        verdictCode = VERDICT_HIT;
        elementCode = ELEMENT_NONE;
        poiseDamage = 0;
        atMs = 0L;
        active = false;
    }

    public void populate(
            long attackerId, long targetId,
            int rawDamage, int finalDamage,
            int verdictCode, int elementCode,
            int poiseDamage, long atMs) {
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.rawDamage = rawDamage;
        this.finalDamage = finalDamage;
        this.verdictCode = verdictCode;
        this.elementCode = elementCode;
        this.poiseDamage = poiseDamage;
        this.atMs = atMs;
        this.active = true;
    }

    public long attackerId() {
        return attackerId;
    }

    public long targetId() {
        return targetId;
    }

    public int rawDamage() {
        return rawDamage;
    }

    public int finalDamage() {
        return finalDamage;
    }

    public int verdictCode() {
        return verdictCode;
    }

    public int elementCode() {
        return elementCode;
    }

    public int poiseDamage() {
        return poiseDamage;
    }

    public long atMs() {
        return atMs;
    }

    public int slotIndex() {
        return slotIndex;
    }

    void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public boolean active() {
        return active;
    }
}
