/**
 * RocketMQ 传输外层信封：生产者将具体 {@link MqMessage} 序列化为 payload JSON，
 * 同时写入 messageType（Java 全类名），消费端先解析 Envelope 再二次反序列化 payload。
 */
package cn.itcast.demo.mymmorpg.support;

public class MqMessageEnvelope {

    /** 内层消息类的全限定名，如 cn.xxx.PlayerKickMqMessage，供 {@link MqMessageTypeRegistry#find} 查 Class */
    private String messageType;
    /** 内层 MqMessage 的 JSON 字符串，由 ObjectMapper.readValue(payload, type) 转为具体对象 */
    private String payload;

    /** Jackson 反序列化 Envelope 时使用；messageType/payload 由 JSON 字段填充 */
    public String getMessageType() {
        return messageType;
    }

    /** 生产者组装 Envelope 时设置 messageType，通常为 message.getClass().getName() */
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    /** 消费端读取 payload 原始 JSON，再按 messageType 对应的 Class 做第二次反序列化 */
    public String getPayload() {
        return payload;
    }

    /** 生产者将 MqMessage 对象 toJson 后写入 payload 字段 */
    public void setPayload(String payload) {
        this.payload = payload;
    }
}
