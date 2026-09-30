package cn.itcast.demo.mymmorpg.world.explore;

/**
 * 大世界非战斗探索内容类型：驱动「探索 → 奖励」循环。
 */
public enum ExplorationContentKind {
    /** 解谜机关 */
    PUZZLE,
    /** 宝箱 */
    CHEST,
    /** 收集品（鸟蛋、碑文拓片等） */
    COLLECTIBLE,
    /** 观景点 / 景观打卡 */
    VISTA,
    /** 隐藏洞窟 / 彩蛋入口 */
    SECRET
}
