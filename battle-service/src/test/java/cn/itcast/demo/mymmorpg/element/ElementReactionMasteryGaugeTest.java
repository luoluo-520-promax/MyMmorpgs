package cn.itcast.demo.mymmorpg.element;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementReactionMasteryGaugeTest {

    @Test
    public void masteryAmplifiesReactionDamage() {
        ElementReactionEngine engine = new ElementReactionEngine();
        engine.resolve(1L, 10L, ElementType.PYRO, 100, 0, 0, 2, 0);
        ReactionResult low = engine.resolve(1L, 10L, ElementType.HYDRO, 100, 0, 0, 1, 0);

        ElementReactionEngine engine2 = new ElementReactionEngine();
        engine2.resolve(2L, 10L, ElementType.PYRO, 100, 0, 0, 2, 0);
        ReactionResult high = engine2.resolve(2L, 10L, ElementType.HYDRO, 100, 0, 0, 1, 200);

        assertThat(low.reaction()).isEqualTo(ReactionType.VAPORIZE);
        assertThat(high.finalDamage()).isGreaterThan(low.finalDamage());
    }

    @Test
    public void vaporizeLeavesHalfGaugeResidualWhenTriggerStronger() {
        ElementReactionEngine engine = new ElementReactionEngine();
        engine.resolve(3L, 20L, ElementType.HYDRO, 50, 0, 0, 1, 0);
        ReactionResult react = engine.resolve(3L, 20L, ElementType.PYRO, 50, 0, 0, 4, 0);
        assertThat(react.reaction()).isEqualTo(ReactionType.VAPORIZE);
        ElementAura stack = engine.currentAuraStack(3L, 20L);
        assertThat(stack).isNotNull();
        assertThat(stack.element()).isEqualTo(ElementType.PYRO);
        assertThat(stack.gaugeUnits()).isGreaterThan(0);
    }
}
