/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/BagRetCode.java
 * 类型：类
 * 职责：定义背包/道具接口专用返回码，与接口文档附录错误码表一致。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 协议层公共包，背包服务与网关均可引用

/**
 * 背包/道具接口文档附录错误码（写入 Protobuf 响应 retcode 字段）。
 * <p>覆盖使用道具、丢弃、排序、出售等 7xx 消息的业务结果。</p>
 */
public final class BagRetCode { // 不可继承的常量类

    /** 操作成功 */
    public static final int OK = 0; // 背包操作已完成，可继续读取响应体中的物品数据
    /** 背包已满，无法新增或堆叠物品 */
    public static final int BAG_FULL = 1; // 格子数或堆叠上限已达上限
    /** 物品不存在：bagItemId 无效或该格子无物品 */
    public static final int ITEM_NOT_FOUND = 2; // 客户端引用的背包条目在服务端查不到
    /** 物品数量不足：使用/出售/丢弃数量大于持有量 */
    public static final int COUNT_NOT_ENOUGH = 3; // 批量操作或部分消耗时数量校验失败
    /** 等级不足：物品使用有最低等级限制 */
    public static final int LEVEL_NOT_ENOUGH = 4; // 玩家等级低于道具配置要求
    /** 物品已绑定，不可交易或不可丢弃（视业务规则） */
    public static final int ITEM_BOUND = 5; // 绑定态道具禁止部分操作
    /** 使用目标无效：如对错误实体或错误场景使用道具 */
    public static final int INVALID_TARGET = 6; // 目标 ID、类型与道具效果不匹配
    /** 出售价格无效：配置售价为 0 或物品不可出售 */
    public static final int SELL_PRICE_INVALID = 7; // 经济系统拒绝此次出售
    /** 物品不可用：冷却中、场景限制或道具状态异常 */
    public static final int ITEM_UNAVAILABLE = 8; // 综合不可用，非单一字段错误
    /** 非装备类道具 */
    public static final int NOT_EQUIPMENT = 9;
    /** 装备槽位已被占用（应先卸下） */
    public static final int EQUIP_SLOT_OCCUPIED = 10;
    /** 该槽位没有已穿戴装备 */
    public static final int EQUIP_SLOT_EMPTY = 11;

    /**
     * 私有构造器，防止实例化。
     */
    private BagRetCode() { // 仅通过类名访问静态常量
    }
}
