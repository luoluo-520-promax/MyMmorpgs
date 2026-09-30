package cn.itcast.demo.mymmorpg.element;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元素反应新业务流程：弱/强附着量对撞 → 精通放大 → 残留 → 再次反应。
 */
public class ElementReactionBusinessFlowTest {

    @Test
    public void attachTriggerMasteryResidualChain() {
        ElementReactionEngine engine = new ElementReactionEngine();
        long battleId = 501L;
        long target = 901L;

        // 弱水附着
        ReactionResult attach = engine.resolve(battleId, target, ElementType.HYDRO, 50, 0, 0, 1, 0);
        assertThat(attach.reaction()).isEqualTo(ReactionType.NONE);
        assertThat(engine.currentAuraStack(battleId, target).gaugeUnits()).isEqualTo(1);

        // 强火 + 高精通蒸发：倍率放大，火残留
        ReactionResult vaporize = engine.resolve(battleId, target, ElementType.PYRO, 100, 0, 0, 4, 300);
        assertThat(vaporize.reaction()).isEqualTo(ReactionType.VAPORIZE);
        // 2.0 * (1 + 3*0.1) = 2.6 → 260
        assertThat(vaporize.finalDamage()).isEqualTo(260);
        assertThat(engine.currentAura(battleId, target)).isEqualTo(ElementType.PYRO);
        assertThat(engine.currentAuraStack(battleId, target).gaugeUnits()).isGreaterThan(0);

        // 残留火再吃雷 → 超载清空
        ReactionResult overload = engine.resolve(battleId, target, ElementType.ELECTRO, 80, 0, 0, 1, 100);
        assertThat(overload.reaction()).isEqualTo(ReactionType.OVERLOAD);
        assertThat(overload.finalDamage()).isGreaterThan(80);
        assertThat(engine.currentAura(battleId, target)).isEqualTo(ElementType.NONE);

        engine.clearBattle(battleId);
        assertThat(engine.auraSnapshotForTest()).isEqualTo(Map.of());
    }
}
