package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 战斗服独立部署时无法写回玩家进度的兜底实现。
 * 由 battle-service 的 {@link cn.itcast.demo.mymmorpg.config.BattlePortConfiguration} 显式注册。
 */
public class NoOpPlayerProgressPort implements PlayerProgressPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpPlayerProgressPort.class);

    @Override
    public Player addExp(Player player, int expReward) {
        log.debug("PlayerProgressPort 未实现，跳过加经验 playerId={} exp={}",
                player == null ? null : player.getId(), expReward);
        return player;
    }
}
