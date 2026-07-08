/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/LoginPolicy.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：登录准入策略接口，AccountCredentialManager 登录前调用 allowLogin。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

/**
 * 登录规则策略：默认由 PolicyConfiguration 提供 Java 实现，GroovyLoginPolicy 可热替换（封禁名单、维护模式等）。
 */

public interface LoginPolicy { // LoginPolicy 接口定义
    /**
     * 是否允许该账号名发起登录请求（密码校验之前）。
     * false 时 LoginAdmissionManager 直接拒绝，不查 account 表。
     *
     * @param accountName 用户输入的账号名
     */

    boolean allowLogin(String accountName); // LoginPolicy 逻辑
} // LoginPolicy 类体结束
