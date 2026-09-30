package cn.itcast.demo.mymmorpg.world.explore;

import java.util.List;
import java.util.Map;

/**
 * 探索点配置：非战斗内容 + 与核心养成挂钩的奖励。
 */
public record ExplorationPoint(
        String pointId,
        int worldId,
        int sceneId,
        ExplorationContentKind kind,
        float x,
        float y,
        float z,
        float interactRadius,
        /** 奖励条目：itemId → count；含抽卡资源 / 培养素材 / 外观 */
        List<Map<String, Object>> rewards,
        /** 完成所需探索技能（空=无门槛） */
        String requiredExploreSkill,
        boolean oneShot) {

    public ExplorationPoint {
        if (pointId == null || pointId.isBlank()) {
            throw new IllegalArgumentException("pointId required");
        }
        kind = kind == null ? ExplorationContentKind.COLLECTIBLE : kind;
        rewards = rewards == null ? List.of() : List.copyOf(rewards);
        interactRadius = interactRadius <= 0f ? 3f : interactRadius;
    }
}
