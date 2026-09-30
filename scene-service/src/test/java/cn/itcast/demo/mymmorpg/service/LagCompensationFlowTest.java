package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.sync.SnapshotBuffer;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端预测 / 延迟补偿流程：连续移动写入 500ms 快照 → 回滚校验合法位移 → 拒绝瞬移。
 */
public class LagCompensationFlowTest {

    @Test
    public void moveHistoryRewindAllowsLaggyButRejectsTeleport() {
        SnapshotBuffer buf = SnapshotBuffer.forLagCompensation();
        long t0 = System.currentTimeMillis() - 450;

        // 模拟过去约 450ms 的平滑移动轨迹
        buf.push(t0, 0f, 0f, 0f, 40f);
        buf.push(t0 + 100, 4f, 0f, 0f, 40f);
        buf.push(t0 + 200, 8f, 0f, 0f, 40f);
        buf.push(t0 + 300, 12f, 0f, 0f, 40f);
        buf.push(t0 + 400, 16f, 0f, 0f, 40f);
        assertThat(buf.size()).isGreaterThanOrEqualTo(4);

        SnapshotBuffer.PosSnapshot at200 = buf.rewindTo(t0 + 200);
        assertThat(at200).isNotNull();
        assertThat(at200.x()).isEqualTo(8f);

        // 高延迟包：客户端声称时间落在历史窗口，目标点仍在合理速度内 → 放行
        assertThat(buf.validateWithLagCompensation(t0 + 200, 10f, 0f, 0f, 40f, 5f)).isTrue();

        // 瞬移到远处 → 拒绝（即使有历史）
        assertThat(buf.validateWithLagCompensation(t0 + 200, 5000f, 0f, 0f, 40f, 5f)).isFalse();
    }
}
