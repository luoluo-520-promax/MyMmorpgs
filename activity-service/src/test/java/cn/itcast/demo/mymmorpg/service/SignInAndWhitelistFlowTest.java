package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.ActivityConfigPayload;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签到补签/动态奖励 + 活动 WhiteList/DRAFT 可见性。
 */
public class SignInAndWhitelistFlowTest {

    @Test
    public void makeupSign_andDynamicReward() {
        SignInService svc = new SignInService();
        svc.signToday(1L); // day 1
        assertThat(svc.setDynamicReward(3, 9001, 9).get("ok")).isEqualTo(true);

        Map<String, Object> makeup = svc.makeupSign(1L, 3, SignInService.COST_DIAMOND);
        assertThat(makeup.get("ok")).isEqualTo(true);
        assertThat(makeup.get("makeupDay")).isEqualTo(3);
        assertThat(makeup.get("rewardItemId")).isEqualTo(9001);
        assertThat(makeup.get("rewardCount")).isEqualTo(9);
        assertThat(makeup.get("costAmount")).isEqualTo(50);

        assertThat(svc.makeupSign(1L, 3, "DIAMOND").get("error")).isEqualTo("already_signed_day");
        assertThat(svc.makeupSign(1L, 8, "DIAMOND").get("error")).isEqualTo("invalid_day");
        assertThat(svc.makeupSign(1L, 2, "GOLD").get("error")).isEqualTo("invalid_cost_type");
    }

    @Test
    public void whitelistAndDraftVisibility() {
        ActivityConfigPayload published = new ActivityConfigPayload();
        published.publishStatus = "PUBLISHED";
        published.whiteListOnly = false;
        assertThat(ActivityService.isPublishedForPlayer(published, 1L)).isTrue();

        ActivityConfigPayload draft = new ActivityConfigPayload();
        draft.publishStatus = "DRAFT";
        draft.whiteListOnly = true;
        draft.whiteListPlayerIds = List.of(100L);
        assertThat(ActivityService.isPublishedForPlayer(draft, 100L)).isTrue();
        assertThat(ActivityService.isPublishedForPlayer(draft, 101L)).isFalse();

        ActivityConfigPayload gray = new ActivityConfigPayload();
        gray.publishStatus = "PUBLISHED";
        gray.whiteListOnly = true;
        gray.whiteListPlayerIds = List.of(200L);
        assertThat(ActivityService.isPublishedForPlayer(gray, 200L)).isTrue();
        assertThat(ActivityService.isPublishedForPlayer(gray, 201L)).isFalse();
    }
}
