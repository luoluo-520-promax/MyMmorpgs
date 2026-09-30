package cn.itcast.demo.mymmorpg.world.explore;

import java.util.List;
import java.util.Map;

/**
 * 功能奇观：标志性场景与独特攀爬/解谜玩法绑定（非纯景观）。
 */
public record LandmarkWonder(
        String landmarkId,
        String name,
        int worldId,
        int sceneId,
        float x,
        float y,
        float z,
        /** 立体迷宫层数 */
        int mazeLayers,
        /** 通关所需移动能力：HOOK / GLIDE / CLIMB / WIND_FIELD */
        List<String> requiredTraverseModes,
        /** 解谜位索引（写入 WorldStateBitmap） */
        int puzzleBitIndex,
        /** 通关奖励 */
        List<Map<String, Object>> clearRewards,
        /** 通关后解锁：传送点 / NPC / 商店 */
        List<String> unlockIds) {

    public LandmarkWonder {
        if (landmarkId == null || landmarkId.isBlank()) {
            throw new IllegalArgumentException("landmarkId required");
        }
        name = name == null || name.isBlank() ? landmarkId : name.trim();
        mazeLayers = Math.max(1, mazeLayers);
        requiredTraverseModes = requiredTraverseModes == null
                ? List.of() : List.copyOf(requiredTraverseModes);
        clearRewards = clearRewards == null ? List.of() : List.copyOf(clearRewards);
        unlockIds = unlockIds == null ? List.of() : List.copyOf(unlockIds);
    }
}
