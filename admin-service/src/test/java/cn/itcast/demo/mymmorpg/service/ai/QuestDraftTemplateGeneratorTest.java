package cn.itcast.demo.mymmorpg.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QuestDraftTemplateGeneratorTest {

    @Test
    public void generateHasObjectives() {
        QuestDraftTemplateGenerator gen = new QuestDraftTemplateGenerator(new ObjectMapper());
        ObjectNode doc = gen.generate("调查星轨遗迹的日常任务");
        assertThat(doc.path("name").asText()).isNotBlank();
        assertThat(doc.path("objectives").isArray()).isTrue();
        assertThat(doc.path("objectives").size()).isGreaterThan(0);
        assertThat(doc.path("type").asText()).isEqualTo("DAILY");
    }
}
