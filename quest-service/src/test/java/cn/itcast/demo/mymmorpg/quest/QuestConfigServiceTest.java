package cn.itcast.demo.mymmorpg.quest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QuestConfigServiceTest {

    @Test
    public void reload_loadsClasspathQuests() {
        QuestConfigService service = new QuestConfigService(
                new ObjectMapper(), new DefaultResourceLoader(), "config/quest/Quests.json");
        assertThat(service.reload()).isTrue();
        assertThat(service.listAll()).hasSize(3);
        QuestTemplate newbie = service.find(1001);
        assertThat(newbie).isNotNull();
        assertThat(newbie.completeOnAccept).isTrue();
        assertThat(service.find(1002).completeOnAccept).isFalse();
        assertThat(service.version()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void snapshot_prepareThenSwap_isAtomic() throws Exception {
        QuestConfigService service = new QuestConfigService(
                new ObjectMapper(), new DefaultResourceLoader(), "config/quest/Quests.json");
        assertThat(service.reload()).isTrue();
        int versionBefore = service.version();
        int sizeBefore = service.listAll().size();

        QuestConfigService.QuestSnapshot prepared = service.prepare();
        assertThat(prepared.byId()).hasSize(sizeBefore);
        // prepare 不切换指针
        assertThat(service.version()).isEqualTo(versionBefore);

        service.swapPrepared(prepared);
        assertThat(service.version()).isEqualTo(prepared.version());
        assertThat(service.listAll()).hasSize(sizeBefore);
    }

    @Test
    public void applyQuests_rejectsInvalidAndKeepsPreviousRoot() {
        QuestConfigService service = new QuestConfigService(
                new ObjectMapper(), new DefaultResourceLoader(), "config/quest/Quests.json");
        assertThat(service.reload()).isTrue();
        int versionBefore = service.version();

        QuestTemplate bad = new QuestTemplate();
        bad.questId = 0;
        assertThat(service.applyQuests(java.util.List.of(bad))).isFalse();
        assertThat(service.version()).isEqualTo(versionBefore);
        assertThat(service.find(1001)).isNotNull();
    }
}
