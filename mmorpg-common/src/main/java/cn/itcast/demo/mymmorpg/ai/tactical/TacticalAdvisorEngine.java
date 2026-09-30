package cn.itcast.demo.mymmorpg.ai.tactical;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时战术辅助：规则 + 小型决策树，建议每 2s 由服务端计算推送（不干扰战斗 Tick）。
 */
public final class TacticalAdvisorEngine {

    public record Battlefield(
            double bossHpRatio,
            double partyHpRatio,
            double partyEnergyRatio,
            boolean hasElementShield,
            String weakElement,
            int aliveAdds,
            boolean mechanismReady,
            String mechanismHint) {
    }

    public record Advice(
            String hint,
            String priority,
            String target,
            int urgency,
            long computedAtMs) {
    }

    public Advice advise(Battlefield bf) {
        long now = System.currentTimeMillis();
        if (bf == null) {
            return new Advice("等待战况同步…", "wait", "none", 1, now);
        }
        if (bf.partyHpRatio() < 0.35) {
            return new Advice("队伍残血，优先治疗/减伤并拉开距离。", "survive", "party", 5, now);
        }
        if (bf.hasElementShield()) {
            String el = bf.weakElement() == null || bf.weakElement().isBlank() ? "对应" : bf.weakElement();
            return new Advice("使用" + el + "元素破盾，再集中攻击。", "break_shield", "boss", 4, now);
        }
        if (bf.aliveAdds() >= 3) {
            return new Advice("场上小怪较多，先清杂再打 Boss。", "clear_adds", "adds", 3, now);
        }
        if (bf.mechanismReady() && bf.mechanismHint() != null && !bf.mechanismHint().isBlank()) {
            return new Advice(bf.mechanismHint(), "mechanism", "field", 4, now);
        }
        if (bf.bossHpRatio() < 0.25 && bf.partyEnergyRatio() > 0.5) {
            return new Advice("Boss 残血，释放爆发技能集火核心。", "burst", "boss_core", 5, now);
        }
        if (bf.bossHpRatio() > 0.85) {
            return new Advice("开场注意走位，熟悉技能前摇后再全力输出。", "setup", "boss", 2, now);
        }
        return new Advice("保持输出循环，注意红圈与点名技能。", "dps", "boss", 2, now);
    }

    public Map<String, Object> toMap(Advice a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hint", a.hint());
        m.put("priority", a.priority());
        m.put("target", a.target());
        m.put("urgency", a.urgency());
        m.put("computedAtMs", a.computedAtMs());
        m.put("refreshHintSec", 2);
        return m;
    }
}
