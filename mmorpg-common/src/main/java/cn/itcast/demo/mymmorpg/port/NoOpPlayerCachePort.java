package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.entity.Player;

/**
 * 无 player-service 玩家缓存时的兜底实现。
 */
public class NoOpPlayerCachePort implements PlayerCachePort {

    @Override
    public Player findById(long playerId) {
        return null;
    }

    @Override
    public boolean existsById(long playerId) {
        return false;
    }

    @Override
    public Player saveCacheAndMarkDirty(Player player) {
        return player;
    }
}
