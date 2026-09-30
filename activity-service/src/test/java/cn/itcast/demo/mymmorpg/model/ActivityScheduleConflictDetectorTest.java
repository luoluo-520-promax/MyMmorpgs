package cn.itcast.demo.mymmorpg.model;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动时间轴互斥冲突 + 预发布 staging 校验。
 */
public class ActivityScheduleConflictDetectorTest {

    @Test
    public void detect_mutexOverlap() {
        ActivityImportDocument a = doc(10, 1000, 2000);
        ActivityImportDocument b = doc(11, 1500, 2500);
        List<String> errors = ActivityScheduleConflictDetector.detect(List.of(a, b));
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("时间轴冲突");
    }

    @Test
    public void detect_sameTypeOrNoOverlap_ok() {
        assertThat(ActivityScheduleConflictDetector.detect(List.of(
                doc(10, 1000, 2000), doc(10, 1500, 2500)))).isEmpty();
        assertThat(ActivityScheduleConflictDetector.detect(List.of(
                doc(10, 1000, 2000), doc(11, 3000, 4000)))).isEmpty();
    }

    @Test
    public void stagingValidate_includesConflict() {
        ActivityImportDocument a = validDoc(10, 1000, 2000);
        ActivityImportDocument b = validDoc(11, 1500, 2500);
        Map<String, Object> out = ActivityScheduleConflictDetector.stagingValidate(List.of(a, b));
        assertThat(out.get("ok")).isEqualTo(false);
        assertThat((Integer) out.get("conflictCount")).isGreaterThan(0);
        assertThat(out.get("staging")).isEqualTo(true);
    }

    private static ActivityImportDocument doc(int type, long start, long end) {
        ActivityImportDocument d = new ActivityImportDocument();
        d.type = type;
        d.startTime = start;
        d.endTime = end;
        d.name = "act-" + type;
        return d;
    }

    private static ActivityImportDocument validDoc(int type, long start, long end) {
        ActivityImportDocument d = doc(type, start, end);
        d.rewardMethod = 1;
        RewardTierPayload tier = new RewardTierPayload();
        tier.index = 1;
        tier.itemId = 1;
        tier.count = 1;
        d.rewardTiers = List.of(tier);
        return d;
    }
}
