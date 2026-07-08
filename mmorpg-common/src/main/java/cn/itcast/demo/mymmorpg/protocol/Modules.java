/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/Modules.java
 * 类型：接口
 * 职责：定义功能模块 ID 分段，用于计算全局消息 ID 与跨服路由。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 模块编号常量

/**
 * 功能模块 ID 分段：基础设施（约 -128 至 -1）与业务（0 及以上）。
 * <p>客户端/服务端约定：{@code msgId = module * 100 + cmd}，与 {@link MessageId} 数值对齐。</p>
 */
public interface Modules { // 接口形式存放 int 常量，Java 中字段默认为 public static final

    /** 基础设施模块号下界（含） */
    int INFRA_MIN = -128; // 负数段预留给 GM、跨服等系统级模块
    /** 基础设施模块号上界（含） */
    int INFRA_MAX = -1; // 与业务 module >= 0 区分

    /** GM 指令模块 */
    int GM = -2; // 运维/调试类消息，不走常规业务分段
    /** 跨服 RPC / 集群协调模块 */
    int CROSS_SERVER = -3; // 服务器间通信，非客户端直连消息

    /**
     * 客户端/服务器消息分段约定：msgId = module * 100 + cmd。
     * <p>与 {@link MessageId} 的定义保持一致；例如 SCENE=1 且 cmd=1 则 msgId=101。</p>
     */
    int AUTH = 0;      // 0xx：登录、选角、登出
    int SCENE = 1;     // 1xx：场景进入、移动、实体同步、切线
    int BATTLE = 2;    // 2xx：开战、战斗操作、战斗同步与结束
    int SKILL = 3;     // 3xx：技能列表、学习、释放、冷却推送
    int BUFF = 4;      // 4xx：Buff 查询、增删改推送
    int CHAT = 6;      // 6xx：聊天发送与广播（5xx 预留未用）
    int BAG = 7;       // 7xx：背包查询、使用、丢弃、排序、出售
    int ACTIVITY = 8;  // 8xx：活动列表、详情、领奖、状态推送
}
