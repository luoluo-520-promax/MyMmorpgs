package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.entity.Player;

/**
 * 跨模块读写玩家实体缓存，由 player-service 提供实现。
 */
public interface PlayerCachePort {

    Player findById(long playerId);

    boolean existsById(long playerId);

    Player saveCacheAndMarkDirty(Player player);
}
