package cn.itcast.demo.mymmorpg.service.ai;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiDraftVersionStoreTest {

    @Test
    public void draftSubmitApprovePublish_flow() {
        AiDraftVersionStore store = new AiDraftVersionStore();
        AiDraftVersionStore.DraftVersion draft = store.create(1L, "activity", "{\"name\":\"x\"}", "v1");
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(store.submit(draft.draftId(), 1L)).isPresent();
        assertThat(store.approve(draft.draftId(), 2L, "ok")).isPresent();
        assertThat(store.publish(draft.draftId(), 1L)).isPresent();
        assertThat(store.get(draft.draftId()).orElseThrow().status()).isEqualTo("PUBLISHED");
        assertThat(store.get(draft.draftId()).orElseThrow().history()).isNotEmpty();
    }
}
