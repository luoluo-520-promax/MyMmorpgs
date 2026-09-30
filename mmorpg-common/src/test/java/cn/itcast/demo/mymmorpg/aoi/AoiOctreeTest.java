package cn.itcast.demo.mymmorpg.aoi;

import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class AoiOctreeTest {

    @Test
    public void insertQueryRespectsHeight() {
        AoiOctree tree = new AoiOctree(0, -10, 0, 200, 4, 4);
        tree.insert(1L, 10f, 0f, 10f);
        tree.insert(2L, 12f, 40f, 12f); // 悬崖上方
        List<Long> nearGround = tree.queryIds(10f, 0f, 10f, 20f);
        assertThat(nearGround).contains(1L);
        // 八叉树半径内仍可能包含高处实体；悬崖裁剪由 OcclusionCuller 负责
        Predicate<float[]> cliff = OcclusionCuller.cliffFilter(8f);
        assertThat(cliff.test(new float[]{10, 0, 10, 12, 40, 12})).isFalse();
        assertThat(cliff.test(new float[]{10, 0, 10, 12, 2, 12})).isTrue();
    }

    @Test
    public void benchmarkCompletes() {
        AoiOctree tree = new AoiOctree();
        long ms = tree.benchmarkQueryMillis(1500, 800, 120f);
        assertThat(ms).isGreaterThanOrEqualTo(0L);
        assertThat(tree.entityCount()).isEqualTo(1500);
    }
}
