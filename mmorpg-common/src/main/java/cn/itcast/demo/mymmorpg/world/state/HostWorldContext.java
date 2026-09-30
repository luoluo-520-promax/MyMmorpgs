package cn.itcast.demo.mymmorpg.world.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 房主世界上下文：联机半单机模式下以房主世界等级 / 进度压制访客。
 */
public record HostWorldContext(
        long hostPlayerId,
        int worldId,
        int worldLevel,
        int adventureRank,
        List<Long> memberPlayerIds,
        boolean suppressVisitorWorldLevel) {

    public HostWorldContext {
        Objects.requireNonNull(memberPlayerIds, "memberPlayerIds");
        worldLevel = Math.max(0, worldLevel);
        adventureRank = Math.max(1, adventureRank);
    }

    public boolean isMember(long playerId) {
        if (playerId == hostPlayerId) {
            return true;
        }
        return memberPlayerIds.contains(playerId);
    }

    /**
     * 访客进入房主世界时使用的有效世界等级：默认压制到房主等级，避免访客碾压。
     */
    public int effectiveWorldLevelFor(long playerId, int visitorWorldLevel) {
        if (playerId == hostPlayerId || !suppressVisitorWorldLevel) {
            return Math.max(worldLevel, visitorWorldLevel);
        }
        return worldLevel;
    }

    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hostPlayerId", hostPlayerId);
        m.put("worldId", worldId);
        m.put("worldLevel", worldLevel);
        m.put("adventureRank", adventureRank);
        m.put("members", memberPlayerIds);
        m.put("suppressVisitorWorldLevel", suppressVisitorWorldLevel);
        return m;
    }
}
