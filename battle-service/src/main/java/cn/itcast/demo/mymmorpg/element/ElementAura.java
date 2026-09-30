package cn.itcast.demo.mymmorpg.element;

/**
 * 目标身上的元素附着（Aura），含 GaugeUnit 元素量（弱1 / 强2 / 超强4）。
 */
public record ElementAura(ElementType element, int stacks, long expireAtMs, int gaugeUnits) {

    /** 兼容旧构造：默认弱附着 1U。 */
    public ElementAura(ElementType element, int stacks, long expireAtMs) {
        this(element, stacks, expireAtMs, Math.max(1, stacks));
    }

    public boolean isExpired(long nowMs) {
        return element == ElementType.NONE || nowMs > expireAtMs || stacks <= 0 || gaugeUnits <= 0;
    }

    public ElementAura withStacks(int newStacks) {
        return new ElementAura(element, Math.max(0, newStacks), expireAtMs, gaugeUnits);
    }

    public ElementAura withGauge(int units) {
        int g = Math.max(0, units);
        return new ElementAura(element, Math.max(1, (g + 1) / 2), expireAtMs, g);
    }

    /** 弱=1 / 强=2 / 超强=4 */
    public static int normalizeGauge(int gauge) {
        if (gauge >= 4) {
            return 4;
        }
        if (gauge >= 2) {
            return 2;
        }
        return Math.max(1, gauge);
    }
}
