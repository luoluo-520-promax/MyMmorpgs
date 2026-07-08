package cn.itcast.demo.mymmorpg.port;

/**
 * 无 player-service 预加载服务时的兜底：不触发异步加载。
 */
public class NoOpPlayerDataPreloadPort implements PlayerDataPreloadPort {

    @Override
    public void trigger(long playerId, PlayerDataLoadPort.DataType type) {
        // no-op
    }
}
