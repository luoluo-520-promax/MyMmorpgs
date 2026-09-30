package cn.itcast.demo.mymmorpg.aoi;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AoiGridTest {

    @Test
    public void insertQueryAndMove() {
        AoiGrid grid = new AoiGrid(100);
        grid.insert(1L, 10f, 10f);
        grid.insert(2L, 550f, 10f);
        List<Long> near = grid.queryIds(10f, 10f, 80f);
        assertThat(near).contains(1L);
        assertThat(near).doesNotContain(2L);

        long cell = grid.cellKey(10f, 10f);
        long newCell = grid.move(1L, cell, 160f, 10f);
        assertThat(newCell).isNotEqualTo(cell);
        assertThat(grid.queryIds(160f, 10f, 50f)).contains(1L);
    }

    @Test
    public void benchmarkCompletesForThousandEntities() {
        AoiGrid grid = new AoiGrid(100);
        long ms = grid.benchmarkQueryMillis(2000, 1000, 300f);
        assertThat(ms).isGreaterThanOrEqualTo(0L);
        assertThat(grid.cellCount()).isGreaterThan(0);
    }
}
