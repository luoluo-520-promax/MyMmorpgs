package cn.itcast.demo.mymmorpg.ai.bt;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BehaviorTreeTest {

    @Test
    public void bossTree_enragesBelowThirtyPercent() {
        BehaviorTree.Node tree = BehaviorTree.defaultBossTree();
        BehaviorTree.Blackboard bb = new BehaviorTree.Blackboard();
        bb.put("hpRatio", 0.2);
        bb.put("phase", 1);
        bb.put("threatTargetId", 42L);
        BehaviorTree.Status status = tree.tick(bb);
        assertThat(status).isEqualTo(BehaviorTree.Status.SUCCESS);
        assertThat(bb.get("enraged", false)).isEqualTo(true);
        assertThat(bb.get("lastAction", "")).isEqualTo("enrage");
    }

    @Test
    public void bossTree_phaseShiftAtSixtyPercent() {
        BehaviorTree.Node tree = BehaviorTree.defaultBossTree();
        BehaviorTree.Blackboard bb = new BehaviorTree.Blackboard();
        bb.put("hpRatio", 0.55);
        bb.put("phase", 1);
        bb.put("threatTargetId", 1L);
        tree.tick(bb);
        assertThat(bb.get("phase", 1)).isEqualTo(2);
        assertThat(bb.get("skill", "")).isEqualTo("phase2_aoe");
    }
}
