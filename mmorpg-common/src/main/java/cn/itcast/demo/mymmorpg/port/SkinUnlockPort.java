package cn.itcast.demo.mymmorpg.port;

/**
 * 背包使用皮肤卡 / 商城履约后解锁皮肤的跨服务端口。
 */
public interface SkinUnlockPort {

    /** 按皮肤解锁道具 itemId 解锁，返回 {@link cn.itcast.demo.mymmorpg.protocol.RetCode}。 */
    int unlockByItem(long playerId, int itemId);

    /** 按 skinId 直发，返回 {@link cn.itcast.demo.mymmorpg.protocol.RetCode}。 */
    int grantSkin(long playerId, int skinId);
}
