/**
 * 聊天消息事件发布抽象：世界/公会/私聊发送成功后投递 MQ，
 * 供聊天日志归档、敏感词审计、跨服转发等异步处理。
 */
package cn.itcast.demo.mymmorpg.service;

public interface ChatEventPublisher { // 聊天事件对外发布契约：把玩家交流内容交给异步链路处理

    /**
     * 发布「聊天消息已发送」事件。
     *
     * @param senderId  发送者 playerId
     * @param channel   频道类型（世界/附近/公会/私聊等）
     * @param targetId  私聊目标 playerId，非私聊时为 0
     * @param msgType   消息类型（文本/表情/系统公告等）
     * @param content   消息正文（MQ 侧可做脱敏或截断）
     * @param serverTs  服务端时间戳，用于排序与防重放
     */
    void publishChatSent(long senderId, int channel, long targetId, int msgType, String content, long serverTs); // 广播聊天消息，供审计、存档与跨服同步消费
}
