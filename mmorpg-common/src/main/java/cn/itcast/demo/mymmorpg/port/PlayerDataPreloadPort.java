package cn.itcast.demo.mymmorpg.port;

/**
 * 玩家分片数据异步预加载触发能力，由 player-service 提供实现。
 */
public interface PlayerDataPreloadPort {

    void trigger(long playerId, PlayerDataLoadPort.DataType type);
}
