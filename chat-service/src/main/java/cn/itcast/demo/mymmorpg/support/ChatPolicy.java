/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/java/cn/itcast/demo/mymmorpg/support/ChatPolicy.java
 * 2) 所属模块：chat-service / support
 * 3) 主要职责：聊天内容过滤与频道权限策略，ChatService 发送前调用 filterContent。
 * 4) 变更建议：修改前先确认 ChatService.handleSendChat 与 602/603 协议字段一致。
 * 5) 风险提示：filterContent 返回 null 会拒绝发送，公式变更需回归敏感词与频道解锁用例。
 */
package cn.itcast.demo.mymmorpg.support; // 聊天策略接口包，ChatPolicyConfiguration 与 GroovyChatPolicy 均实现本接口

import org.springframework.lang.Nullable; // 标注 filterContent 可返回 null 表示拒绝发送，消除调用方空指针告警

/**
 * 聊天策略：ChatPolicyConfiguration 提供默认敏感词+截断，GroovyChatPolicy 可热替换接入第三方审核 API。
 */
public interface ChatPolicy { // 策略模式接口：将内容审核与频道权限从 ChatService 业务逻辑中解耦

    /**
     * 过滤聊天正文：空白/敏感词拒绝（返回 null），超长截断，通过则返回处理后字符串。
     * ChatService 在写 Redis 审计与推送 603 之前调用。
     *
     * @param channel    SendChatMsgCsReq.channel：1私聊 2世界 3队伍 4公会，策略可按频道差异化
     * @param msgType    SendChatMsgCsReq.msg_type：1文本 2表情 3系统，表情可跳过敏感词
     * @param rawContent 客户端 SendChatMsgCsReq.content 原始字符串，未经 trim
     * @return 过滤后正文；null 表示拒绝发送，ChatService 回 ChatRetCode.SENSITIVE
     */
    @Nullable // 明确告知调用方返回值可能为 null，需判空后决定 602 retcode
    String filterContent(int channel, int msgType, String rawContent); // ChatService.handleSendChat 第 141 行调用

    /**
     * 判断发送者是否可使用指定频道：可结合玩家等级、VIP、FunctionBox 解锁队伍/公会频道。
     * ChatPolicyConfiguration 匿名实现未 override，沿用本 default。
     *
     * @param senderPlayerId WebSocket 绑定的发送者 player.id
     * @param channel        目标频道 1~4，对应 ChatService.CHANNEL_* 常量
     * @return true 允许发送；false 时 ChatService 回 ChatRetCode.CHANNEL_UNAVAILABLE
     */
    default boolean isChannelUnlocked(long senderPlayerId, int channel) { // GroovyChatPolicy 可 override 接入功能盒校验
        return true; // 默认全部频道开放，队伍/公会频道实际还依赖 Redis roster 是否存在
    }
}
