package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin 草稿保存 → 一键全量发布。
 */
public class ConfigDraftPublishServiceTest {

    @Test
    public void saveDraft_thenPublishAll() {
        ConfigDraftPublishService svc = new ConfigDraftPublishService();
        Map<String, Object> draft = svc.saveDraft(ConfigDraftPublishService.Domain.ACTIVITY, "{\"type\":1}");
        assertThat(draft.get("ok")).isEqualTo(true);
        assertThat(draft.get("publishStatus")).isEqualTo("DRAFT");
        assertThat(svc.getDraft(ConfigDraftPublishService.Domain.ACTIVITY)).contains("type");

        svc.saveDraft(ConfigDraftPublishService.Domain.SHOP, "{\"productId\":1}");
        Map<String, Object> published = svc.publishAll();
        assertThat(published.get("ok")).isEqualTo(true);
        assertThat(published.get("publishedCount")).isEqualTo(2);
        assertThat(svc.getDraft(ConfigDraftPublishService.Domain.ACTIVITY)).isNull();
        assertThat(svc.getPublished(ConfigDraftPublishService.Domain.SHOP)).contains("productId");
    }

    @Test
    public void publish_withoutDraft_fails() {
        ConfigDraftPublishService svc = new ConfigDraftPublishService();
        assertThat(svc.publish(ConfigDraftPublishService.Domain.ACTIVITY).get("error"))
                .isEqualTo("no_draft");
    }
}
