package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.NpcAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class NpcDialogueServiceTest {

    @Test
    public void ruleFallbackGivesQuestClue() {
        NpcAiProperties props = new NpcAiProperties();
        props.setEnabled(true);
        props.setLlmEnabled(false);
        NpcDialogueService svc = new NpcDialogueService(props, new ObjectMapper(), new NpcDialogueMemory());
        Map<String, Object> rsp = svc.talk(1L, "guide", "有什么任务线索吗");
        assertThat(rsp.get("ok")).isEqualTo(true);
        assertThat(rsp.get("source")).isEqualTo("rule");
        assertThat(rsp.get("clues")).asList().isNotEmpty();
    }
}
