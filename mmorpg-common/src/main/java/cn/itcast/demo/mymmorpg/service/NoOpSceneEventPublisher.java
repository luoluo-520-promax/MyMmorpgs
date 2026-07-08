/**
 * 场景事件空实现：rocketmq.enabled=false（或未配置）时使用，
 * 仅 debug 日志记录事件参数，不连接 Broker，适合本地开发与单体调试。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger; // debug 级别记录被跳过的场景事件
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 与 RocketMqSceneEventPublisher 互斥
import org.springframework.stereotype.Component;

@Component // MQ 关闭时作为 SceneEventPublisher 的唯一实现
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true) // 默认不启用 MQ
class NoOpSceneEventPublisher implements SceneEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpSceneEventPublisher.class);

    @Override
    public void publishEnterScene(long playerId, int sceneId, int lineId) {
        log.debug("MQ 未启用，跳过 enterScene 事件 playerId={} sceneId={} lineId={}", playerId, sceneId, lineId);
    }

    @Override
    public void publishLeaveScene(long playerId, int sceneId) {
        log.debug("MQ 未启用，跳过 leaveScene 事件 playerId={} sceneId={}", playerId, sceneId);
    }

    @Override
    public void publishSwitchLine(long playerId, int sceneId, int fromLine, int toLine) {
        log.debug("MQ 未启用，跳过 switchLine 事件 playerId={} sceneId={} {}->{}", playerId, sceneId, fromLine, toLine);
    }

    @Override
    public void publishMove(long playerId, int sceneId, float x, float y, float z) {
        log.debug("MQ 未启用，跳过 move 事件 playerId={} sceneId={} pos=({},{},{})", playerId, sceneId, x, y, z);
    }
}
