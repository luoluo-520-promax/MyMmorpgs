/**
 * 聊天事件空实现：MQ 关闭时不向 CHAT_EVENTS Topic 投递，避免本地无 Broker 时启动失败。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true)
class NoOpChatEventPublisher implements ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpChatEventPublisher.class);

    @Override
    public void publishChatSent(long senderId, int channel, long targetId, int msgType, String content, long serverTs) {
        // 不记录 content 到 debug，避免日志泄露聊天正文；仅记录 sender/channel/type 供联调
        log.debug("MQ 未启用，跳过 chat 事件 sender={} channel={} type={}", senderId, channel, msgType);
    }
}
