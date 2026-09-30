package cn.itcast.demo.mymmorpg.ai.npc;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class NpcLongTermMemoryTest {

    @Test
    public void emotionAndWeightsEvolve() {
        NpcLongTermMemory mem = new NpcLongTermMemory();
        mem.interact(1, "kate", NpcEmotionVector.InteractionKind.GIFT, "送花", 0.8);
        mem.interact(1, "kate", NpcEmotionVector.InteractionKind.QUEST_HELP, "帮忙", 0.6);
        Map<String, Object> snap = mem.toMap(1, "kate");
        Assert.assertEquals(snap.get("giftCount"), 1);
        Assert.assertEquals(snap.get("questHelps"), 1);
        @SuppressWarnings("unchecked")
        Map<String, Double> emo = (Map<String, Double>) snap.get("emotion");
        Assert.assertTrue(emo.get("affection") > 0.3);
        @SuppressWarnings("unchecked")
        Map<String, Double> weights = (Map<String, Double>) snap.get("behaviorWeights");
        Assert.assertTrue(weights.get("assist") > 0.2);
    }

    @Test
    public void attackRaisesFearAndAnger() {
        NpcEmotionVector.Emotion e = NpcEmotionVector.Emotion.neutral();
        e = NpcEmotionVector.apply(e, NpcEmotionVector.InteractionKind.PLAYER_ATTACK, 1.0);
        Assert.assertTrue(e.anger() > 0.2);
        Assert.assertTrue(e.fear() > 0.1);
        Assert.assertNotEquals(e.dominantMood(), "FRIENDLY");
    }
}
