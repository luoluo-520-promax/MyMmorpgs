/**
 * 文件说明：RPC JSON 编解码器单元测试。
 * 职责：验证 RpcJsonCodec 对 RpcWireEnvelope 的包装与 body 反序列化。
 */
package cn.itcast.demo.mymmorpg.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcJsonCodecTest {

    private static final Logger log = LoggerFactory.getLogger(RpcJsonCodecTest.class);

    private RpcJsonCodec codec;

    @BeforeMethod
    public void setUp() {
        codec = new RpcJsonCodec(new ObjectMapper());
        log.info("[测试前置] RpcJsonCodec 已加载");
    }

    @Test
    public void wrap_setsKindAndBody() {
        long requestId = 90001L;
        long playerId = 10086L;
        int msgId = 2001;
        byte[] payload = new byte[]{1, 2, 3};
        RpcForwardClientMessage message = new RpcForwardClientMessage(requestId, playerId, msgId, payload);
        log.info("[测试开始] 场景=包装RPC信封 | requestId={} | playerId={} | msgId={} | payloadLen={}",
                requestId, playerId, msgId, payload.length);

        RpcWireEnvelope envelope = codec.wrap(message);

        log.info("[测试断言] 场景=包装RPC信封 | kind={} | bodyClass={}",
                envelope.getKind(), envelope.getBody().getClass().getSimpleName());
        assertThat(envelope.getKind()).isEqualTo("RpcForwardClientMessage");
        assertThat(envelope.getBody()).isSameAs(message);
    }

    @Test
    public void wrap_nullMessage_returnsEmptyEnvelope() {
        log.info("[测试开始] 场景=空消息包装 | message=null");

        RpcWireEnvelope envelope = codec.wrap(null);

        log.info("[测试断言] 场景=空消息包装 | kind={} | body={}", envelope.getKind(), envelope.getBody());
        assertThat(envelope.getKind()).isNull();
        assertThat(envelope.getBody()).isNull();
    }

    @Test
    public void parseBody_convertsMapToPojo() {
        long requestId = 90002L;
        long playerId = 20001L;
        int msgId = 3002;
        log.info("[测试开始] 场景=反序列化body | requestId={} | playerId={} | msgId={}",
                requestId, playerId, msgId);

        var bodyMap = java.util.Map.of(
                "requestId", requestId,
                "playerId", playerId,
                "msgId", msgId,
                "payload", new byte[]{9, 8, 7}
        );
        RpcForwardClientMessage parsed = codec.parseBody(bodyMap, RpcForwardClientMessage.class);

        log.info("[测试断言] 场景=反序列化body | parsedRequestId={} | parsedPlayerId={} | parsedMsgId={}",
                parsed.getRequestId(), parsed.getPlayerId(), parsed.getMsgId());
        assertThat(parsed.getRequestId()).isEqualTo(requestId);
        assertThat(parsed.getPlayerId()).isEqualTo(playerId);
        assertThat(parsed.getMsgId()).isEqualTo(msgId);
    }
}
