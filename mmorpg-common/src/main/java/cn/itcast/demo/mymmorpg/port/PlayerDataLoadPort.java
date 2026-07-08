/**
 * 文件说明
 * 模块：mmorpg-common / 端口接口
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/port/PlayerDataLoadPort.java
 * 类型：接口
 * 职责：定义 PlayerDataLoadPort，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.port;

/**
 * 玩家分片数据异步预加载就绪状态
 */
/**
 * 接口 PlayerDataLoadPort：封装相关业务逻辑与数据结构。
 */
public interface PlayerDataLoadPort {

    /**
     * 接口 PlayerDataLoadPort：封装相关业务逻辑与数据结构。
     */
    enum DataType {
        BAG,  // 执行代码
        SKILL,  // 执行代码
        ACTIVITY  // 执行代码
    }

    /**
     * 判断ready是否为真
     */
    boolean isReady(long playerId, DataType type);
}
