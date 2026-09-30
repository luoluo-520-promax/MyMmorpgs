package cn.itcast.demo.mymmorpg.port;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 独立 bag-service 且未开启远程 Port 时的降级实现。
 */
public class NoOpSkinUnlockPort implements SkinUnlockPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSkinUnlockPort.class);

    @Override
    public int unlockByItem(long playerId, int itemId) {
        log.warn("SkinUnlockPort 未实现，跳过 unlockByItem playerId={} itemId={}", playerId, itemId);
        return RetCode.INTERNAL_ERROR;
    }

    @Override
    public int grantSkin(long playerId, int skinId) {
        log.warn("SkinUnlockPort 未实现，跳过 grantSkin playerId={} skinId={}", playerId, skinId);
        return RetCode.INTERNAL_ERROR;
    }
}
