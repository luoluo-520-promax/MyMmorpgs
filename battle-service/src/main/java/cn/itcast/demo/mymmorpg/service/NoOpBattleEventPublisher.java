/**
 * 文件说明：战斗事件发布器的空实现（No-Op）。
 * 职责：当 RocketMQ 未启用时，以 debug 日志代替实际消息发送，避免业务代码分支判断。
 * 激活条件：{@code rocketmq.enabled=false} 或未配置时默认启用。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按配置属性条件注册 Bean
import org.springframework.stereotype.Component; // 标记为 Spring 组件

/**
 * MQ 关闭时的战斗事件空实现：仅记录 debug 日志，不发送消息。
 */
@Component // MQ 关闭时注册（默认）
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true) // 未配置或为 false 时使用此类
class NoOpBattleEventPublisher implements BattleEventPublisher { // 空实现：不发送 MQ

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(NoOpBattleEventPublisher.class); // debug 日志

    @Override
    public void publishBattleStarted(long playerId, long battleId, int sceneId, long enemyEntityId, int monsterTemplateId) { // 战斗开始事件
        log.debug("MQ 未启用 battleStart {} {}", playerId, battleId); // 开发环境可观测
    }

    @Override
    public void publishBattleEnded(long playerId, long battleId, int result, int expReward, int durationSec) { // 战斗结束事件
        log.debug("MQ 未启用 battleEnd {} {}", playerId, battleId); // 记录战斗结束日志
    }
}
