/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/model/ActivityTypes.java
 * 2) 所属模块：activity-service / model
 * 3) 主要职责：定义活动类型枚举，与 activity.type 及协议文档保持一致
 * 4) 系统位置：领域模型层，供业务逻辑与策略扩展引用
 * 5) 变更建议：新增活动玩法时追加枚举值并同步协议文档
 */
package cn.itcast.demo.mymmorpg.model;

/**
 * 活动类型（与 activity.type / 接口文档一致，可扩展）。
 */
public enum ActivityTypes { // 活动类型枚举

    /** 首充活动。 */
    FIRST_RECHARGE(1), // 类型编码 1
    /** 夏日签到活动。 */
    SUMMER_SIGN_IN(2); // 类型编码 2

    /** 对应数据库与协议中的类型编码。 */
    private final int code; // 整型类型码

    /**
     * 构造枚举常量。
     *
     * @param code 类型编码
     */
    ActivityTypes(int code) {
        this.code = code; // 保存类型编码
    } // 构造器结束

    /**
     * 获取类型编码。
     *
     * @return 整型类型码
     */
    public int getCode() {
        return code; // 返回编码
    } // getCode 结束

    /**
     * 根据编码解析枚举，未知编码返回 null。
     *
     * @param code 类型编码
     * @return 匹配的枚举或 null
     */
    public static ActivityTypes fromCode(int code) {
        for (ActivityTypes t : values()) { // 遍历所有枚举常量
            if (t.code == code) { // 编码匹配
                return t; // 返回对应枚举
            } // if 结束
        } // for 结束
        return null; // 未找到时返回 null
    } // fromCode 结束
} // ActivityTypes 枚举结束
