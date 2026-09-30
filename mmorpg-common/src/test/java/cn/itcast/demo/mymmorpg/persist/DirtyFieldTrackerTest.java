package cn.itcast.demo.mymmorpg.persist;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DirtyFieldTrackerTest {

    @Test
    public void markAndDrain() {
        DirtyFieldTracker t = new DirtyFieldTracker();
        t.mark(1L, DirtyFieldTracker.Field.POSITION);
        t.mark(1L, DirtyFieldTracker.Field.EXP);
        assertThat(t.isDirty(1L)).isTrue();
        assertThat(t.drain(1L)).contains(
                DirtyFieldTracker.Field.POSITION, DirtyFieldTracker.Field.EXP);
        assertThat(t.isDirty(1L)).isFalse();
    }
}
