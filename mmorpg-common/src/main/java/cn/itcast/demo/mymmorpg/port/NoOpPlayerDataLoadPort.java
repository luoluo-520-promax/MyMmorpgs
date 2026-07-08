package cn.itcast.demo.mymmorpg.port;

/**
 * 无 player-service 预加载服务时，默认视为数据已就绪（不阻塞列表/详情请求）。
 * 由 activity-service 的 {@link cn.itcast.demo.mymmorpg.config.ActivityPortConfiguration} 显式注册。
 */
public class NoOpPlayerDataLoadPort implements PlayerDataLoadPort {

    @Override
    public boolean isReady(long playerId, DataType type) {
        return true;
    }
}
