package cn.itcast.demo.mymmorpg.element;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ElementReactionEngineTest {

    @Test
    public void pyroThenElectro_triggersOverloadAndAmplifies() {
        ElementReactionEngine engine = new ElementReactionEngine();
        ReactionResult attach = engine.resolve(1L, 99L, ElementType.PYRO, 100, 0, 0);
        assertThat(attach.reaction()).isEqualTo(ReactionType.NONE);
        assertThat(attach.remainingAura()).isEqualTo(ElementType.PYRO);

        ReactionResult react = engine.resolve(1L, 99L, ElementType.ELECTRO, 100, 200, ReactionType.OVERLOAD.getCode());
        assertThat(react.reaction()).isEqualTo(ReactionType.OVERLOAD);
        assertThat(react.finalDamage()).isEqualTo(200);
        assertThat(react.rollback()).isFalse();
    }

    @Test
    public void clientPredictionMismatch_setsRollback() {
        ElementReactionEngine engine = new ElementReactionEngine();
        engine.resolve(2L, 7L, ElementType.CRYO, 50, 0, 0);
        ReactionResult react = engine.resolve(2L, 7L, ElementType.HYDRO, 50, 999, ReactionType.FREEZE.getCode());
        assertThat(react.reaction()).isEqualTo(ReactionType.FREEZE);
        assertThat(react.rollback()).isTrue();
    }
}
