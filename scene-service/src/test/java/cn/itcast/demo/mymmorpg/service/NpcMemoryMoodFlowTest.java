package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NPC 情绪/记忆业务流程：多轮对话改变 Mood，跨 clear 后重置。
 */
public class NpcMemoryMoodFlowTest {

    @Test
    public void moodTransitionsAndMemoryTurns() {
        NpcDialogueMemory mem = new NpcDialogueMemory();
        long now = System.currentTimeMillis();
        long playerId = 33L;
        String npc = "guide";

        mem.remember(playerId, npc, "你好呀喜欢这里", "欢迎", now);
        assertThat(mem.mood(playerId, npc, now + 1)).isEqualTo(NpcDialogueMemory.Mood.FRIENDLY);

        mem.remember(playerId, npc, "为什么要打boss？", "小心", now + 1000);
        assertThat(mem.mood(playerId, npc, now + 1001)).isEqualTo(NpcDialogueMemory.Mood.CURIOUS);

        mem.remember(playerId, npc, "世界boss太危险了", "保重", now + 2000);
        assertThat(mem.mood(playerId, npc, now + 2001)).isEqualTo(NpcDialogueMemory.Mood.WORRIED);

        mem.remember(playerId, npc, "太棒了！", "哈哈", now + 3000);
        assertThat(mem.mood(playerId, npc, now + 3001)).isEqualTo(NpcDialogueMemory.Mood.EXCITED);

        assertThat(mem.recentTurns(playerId, npc, now + 4000).size()).isGreaterThanOrEqualTo(4);
        assertThat(mem.moodPrefix(NpcDialogueMemory.Mood.EXCITED)).contains("兴奋");

        // 长期情感记忆：每轮对话写入一条事件
        Map<String, Object> bond = mem.bondSnapshot(playerId, npc);
        assertThat((Integer) bond.get("historySize")).isGreaterThanOrEqualTo(4);
        @SuppressWarnings("unchecked")
        Map<String, Double> emotion = (Map<String, Double>) bond.get("emotion");
        assertThat(emotion.get("affection")).isGreaterThan(0.3);

        mem.clear(playerId, npc);
        assertThat(mem.mood(playerId, npc, now + 5000)).isEqualTo(NpcDialogueMemory.Mood.NEUTRAL);
        assertThat(mem.recentTurns(playerId, npc, now + 5000)).isEmpty();
    }

    @Test
    public void sessionExpiresAfterTtl() {
        NpcDialogueMemory mem = new NpcDialogueMemory();
        long t0 = System.currentTimeMillis();
        mem.remember(1L, "guide", "谢谢", "不客气", t0);
        assertThat(mem.mood(1L, "guide", t0)).isEqualTo(NpcDialogueMemory.Mood.FRIENDLY);
        // 超过 15 分钟 TTL
        long expired = t0 + 16 * 60_000L;
        assertThat(mem.mood(1L, "guide", expired)).isEqualTo(NpcDialogueMemory.Mood.NEUTRAL);
    }
}
