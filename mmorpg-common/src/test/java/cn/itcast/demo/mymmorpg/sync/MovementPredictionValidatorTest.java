package cn.itcast.demo.mymmorpg.sync;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MovementPredictionValidatorTest {

    @Test
    public void moveAcceptsTightDeltaAndRejectsLargeSkew() {
        MovementPredictionValidator v = new MovementPredictionValidator();
        long ts = 1_000_000L;
        MovementPredictionValidator.ValidationResult ok = v.validate(
                1L, MovementPredictionValidator.ActionType.MOVE,
                10.5f, 0f, 10f, 10f, 0f, 10f, ts, ts + 50L);
        assertThat(ok.accepted()).isTrue();
        assertThat(ok.rollbackRequired()).isFalse();

        MovementPredictionValidator.ValidationResult skew = v.validate(
                2L, MovementPredictionValidator.ActionType.MOVE,
                10f, 0f, 10f, 10f, 0f, 10f, ts, ts + 5_000L);
        assertThat(skew.accepted()).isFalse();
        assertThat(skew.reason()).isEqualTo("time_skew");
        assertThat(skew.rollbackRequired()).isTrue();
    }

    @Test
    public void thresholdsWidenFromMoveToTeleport() {
        MovementPredictionValidator v = new MovementPredictionValidator();
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.MOVE).positionDelta())
                .isLessThan(v.thresholdOf(MovementPredictionValidator.ActionType.DASH).positionDelta());
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.DASH).positionDelta())
                .isLessThan(v.thresholdOf(MovementPredictionValidator.ActionType.TELEPORT_SKILL).positionDelta());
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.SKILL_DISPLACE).positionDelta())
                .isBetween(
                        v.thresholdOf(MovementPredictionValidator.ActionType.MOVE).positionDelta(),
                        v.thresholdOf(MovementPredictionValidator.ActionType.TELEPORT_SKILL).positionDelta());

        long ts = 2_000_000L;
        MovementPredictionValidator.ValidationResult teleport = v.validate(
                9L, MovementPredictionValidator.ActionType.TELEPORT_SKILL,
                40f, 0f, 0f, 0f, 0f, 0f, ts, ts);
        assertThat(teleport.accepted()).isTrue();

        MovementPredictionValidator.ValidationResult moveFar = v.validate(
                10L, MovementPredictionValidator.ActionType.MOVE,
                40f, 0f, 0f, 0f, 0f, 0f, ts, ts);
        assertThat(moveFar.accepted()).isFalse();
        assertThat(moveFar.reason()).isEqualTo("position_delta");
    }

    @Test
    public void midDeltaIsCorrected() {
        MovementPredictionValidator v = new MovementPredictionValidator();
        long ts = 3_000_000L;
        // MOVE positionDelta=1.5, hardLimit=3.0 → 2.5 应 corrected
        MovementPredictionValidator.ValidationResult r = v.validate(
                3L, MovementPredictionValidator.ActionType.MOVE,
                12.5f, 0f, 0f, 10f, 0f, 0f, ts, ts);
        assertThat(r.accepted()).isTrue();
        assertThat(r.corrected()).isTrue();
        assertThat(r.reason()).isEqualTo("position_corrected");
    }

    @Test
    public void explorationTraverseModesHaveDedicatedThresholds() {
        MovementPredictionValidator v = new MovementPredictionValidator();
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.GLIDE).positionDelta())
                .isGreaterThan(v.thresholdOf(MovementPredictionValidator.ActionType.MOVE).positionDelta());
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.CLIMB).positionDelta())
                .isLessThan(v.thresholdOf(MovementPredictionValidator.ActionType.HOOK).positionDelta());
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.SWIM)).isNotNull();
        assertThat(v.thresholdOf(MovementPredictionValidator.ActionType.VEHICLE)).isNotNull();

        long ts = 4_000_000L;
        assertThat(v.validate(11L, MovementPredictionValidator.ActionType.GLIDE,
                8f, 5f, 0f, 0f, 0f, 0f, ts, ts).accepted()).isTrue();
        assertThat(v.validate(12L, MovementPredictionValidator.ActionType.CLIMB,
                3f, 2f, 0f, 0f, 0f, 0f, ts, ts).accepted()).isTrue();
    }
}
