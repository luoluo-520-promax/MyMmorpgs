package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.NpcAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 智能 NPC 多轮规则对话流程。 */
public class NpcDialogueFlowTest {

    private NpcDialogueService svc;

    @BeforeMethod
    public void setUp() {
        NpcAiProperties props = new NpcAiProperties();
        props.setEnabled(true);
        props.setLlmEnabled(false);
        svc = new NpcDialogueService(props, new ObjectMapper(), new NpcDialogueMemory());
    }

    @Test
    public void multiTopicRuleDialogue() {
        Map<String, Object> greet = svc.talk(1L, "guide", "");
        assertThat(greet.get("source")).isEqualTo("rule");
        assertThat(String.valueOf(greet.get("reply"))).contains("旅人");

        Map<String, Object> quest = svc.talk(1L, "guide", "有任务线索吗");
        assertThat(((List<?>) quest.get("clues"))).isNotEmpty();

        Map<String, Object> lore = svc.talk(1L, "guide", "讲讲背景故事");
        assertThat(String.valueOf(lore.get("reply"))).contains("星轨");

        Map<String, Object> boss = svc.talk(1L, "guide", "世界BOSS怎么打");
        @SuppressWarnings("unchecked")
        List<String> bossClues = (List<String>) boss.get("clues");
        assertThat(bossClues).contains("activity:world-boss");
        assertThat(boss.get("mood")).isNotNull();
        assertThat(boss.get("memoryTurns")).isNotNull();
    }

    @Test
    public void disabledReturnsError() {
        NpcAiProperties props = new NpcAiProperties();
        props.setEnabled(false);
        NpcDialogueService disabled = new NpcDialogueService(props, new ObjectMapper(), new NpcDialogueMemory());
        assertThat(disabled.talk(1L, "guide", "hi").get("error")).isEqualTo("npc_ai_disabled");
    }
}
