/**
 * 玩家会话事件空实现：rocketmq.enabled=false（或未配置）时 Spring 注入本 Bean，
 * 登录/登出流程照常走通，仅打 debug 日志而不连接 RocketMQ Broker。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger; // debug 级别记录被跳过的事件参数
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 与 RocketMqPlayerEventPublisher 互斥
import org.springframework.stereotype.Component;

@Component // 作为 PlayerEventPublisher 的唯一实现（MQ 关闭时）
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true) // 默认不启用 MQ
class NoOpPlayerEventPublisher implements PlayerEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpPlayerEventPublisher.class);

    @Override
    public void publishAccountLogin(long accountId, String accountName) {
        log.debug("MQ 未启用，跳过 accountLogin 事件 accountId={} name={}", accountId, accountName); // 本地开发时可开 DEBUG 确认调用链
    }

    @Override
    public void publishPlayerEnter(long accountId, long playerId, String playerName) {
        log.debug("MQ 未启用，跳过 playerEnter 事件 accountId={} playerId={} name={}", accountId, playerId, playerName);
    }

    @Override
    public void publishPlayerLogout(long accountId, Long playerId, int reason) {
        log.debug("MQ 未启用，跳过 logout 事件 accountId={} playerId={} reason={}", accountId, playerId, reason);
    }
}
