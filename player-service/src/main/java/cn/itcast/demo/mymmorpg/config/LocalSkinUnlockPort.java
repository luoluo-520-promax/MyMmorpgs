package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.SkinUnlockPort;
import cn.itcast.demo.mymmorpg.service.SkinService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * player-service 进程内皮肤解锁（嵌入式 BagService 使用）。
 */
@Component
@ConditionalOnExpression("'${spring.application.name:}' == 'player-service' && !${game.port.remote.enabled:false}")
public class LocalSkinUnlockPort implements SkinUnlockPort {

    private final SkinService skinService;

    public LocalSkinUnlockPort(SkinService skinService) {
        this.skinService = skinService;
    }

    @Override
    public int unlockByItem(long playerId, int itemId) {
        return skinService.unlockByItem(playerId, itemId);
    }

    @Override
    public int grantSkin(long playerId, int skinId) {
        return skinService.grantSkinRet(playerId, skinId);
    }
}
