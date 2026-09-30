/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/ChatRetCode.java
 * 类型：类
 * 职责：定义聊天发送接口（602）专用返回码。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 协议公共包

/**
 * 聊天发送接口（{@link MessageId#SEND_CHAT_MSG_SC_RSP} = 602）专用返回码。
 * <p>与《聊天系统接口文档》错误码表保持一致。</p>
 */
public final class ChatRetCode { // 聊天子系统错误码，与全局 RetCode 分段独立

    /** 发送成功，消息将进入频道或私聊投递流程 */
    public static final int OK = 0; // 服务端已接受并处理聊天请求
    /** 消息内容包含敏感词，被过滤或拒绝发送 */
    public static final int SENSITIVE = 1; // 敏感词库或审核策略命中
    /** 目标玩家不存在或当前离线（私聊场景） */
    public static final int TARGET_OFFLINE = 2; // 私聊目标不可达
    /** 发送者被禁言，禁止发言 */
    public static final int MUTED = 3; // GM 或系统自动禁言状态
    /** 频道未解锁或当前不可用（等级、场景、活动限制等） */
    public static final int CHANNEL_UNAVAILABLE = 4; // 玩家无权使用该聊天频道
    /** 发送频率过快，触发限流 */
    public static final int RATE_LIMIT = 5; // 防刷屏：单位时间内请求过多

    /**
     * 私有构造器，禁止实例化常量工具类。
     */
    private ChatRetCode() { // 无实例方法，仅提供错误码常量
    }
}
