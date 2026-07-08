/**
 * 场景事件 RocketMQ 发布实现：将进图/离图/切线/移动等行为编码为文本消息，
 * 投递至 SCENE_EVENTS Topic，tags 区分事件类型，供场景服务或数据分析消费。
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer; // 共享生产者 Bean，由 RocketMqProducerConfig 创建并 start
import org.apache.rocketmq.common.message.Message; // RocketMQ 消息对象（Topic + tags + body 字节）
import org.slf4j.Logger; // 发送失败时 warn，不阻断玩家进图/移动主流程
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // 从 yml 读取 scene.mq.topic，默认 SCENE_EVENTS
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅 rocketmq.enabled=true 时注册
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets; // 消息体统一 UTF-8 编码

@Component // 注入到 SceneService 等发布场景事件的组件
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
class RocketMqSceneEventPublisher implements SceneEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqSceneEventPublisher.class);

    /** 全局 RocketMQ 生产者，发送失败时不抛异常以免阻断主游戏流程 */
    private final DefaultMQProducer producer;

    /** 场景事件 Topic，可在 yml 中按环境覆盖 */
    @Value("${scene.mq.topic:SCENE_EVENTS}")
    private String topic;

    RocketMqSceneEventPublisher(DefaultMQProducer producer) {
        this.producer = producer; // 与玩家/技能/道具事件发布器同享一个 producer 实例
    }

    @Override
    public void publishEnterScene(long playerId, int sceneId, int lineId) {
        // tag=enter：玩家进入场景，body 含 playerId/sceneId/lineId 供 AOI/反外挂消费端解析
        send("enter", "enterScene|playerId=" + playerId + "|sceneId=" + sceneId + "|lineId=" + lineId);
    }

    @Override
    public void publishLeaveScene(long playerId, int sceneId) {
        send("leave", "leaveScene|playerId=" + playerId + "|sceneId=" + sceneId); // 离图事件，供清理 AOI 视野
    }

    @Override
    public void publishSwitchLine(long playerId, int sceneId, int fromLine, int toLine) {
        send("switch", "switchLine|playerId=" + playerId + "|sceneId=" + sceneId + "|from=" + fromLine + "|to=" + toLine); // 同场景切分线
    }

    @Override
    public void publishMove(long playerId, int sceneId, float x, float y, float z) {
        send("move", "move|playerId=" + playerId + "|sceneId=" + sceneId + "|pos=" + x + "," + y + "," + z); // 位置同步与异常位移检测
    }

    /**
     * 统一发送入口：构造 Message 并同步 send；失败仅 warn，不影响玩家操作结果。
     */
    private void send(String tags, String body) {
        try {
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8)); // Topic + tag + UTF-8 载荷
            producer.send(msg); // 同步发送，Broker 不可达时进入 catch
        } catch (Exception e) {
            log.warn("RocketMQ 场景事件发送失败 body={}", body, e); // 记录 body 便于事后补数
        }
    }
}
