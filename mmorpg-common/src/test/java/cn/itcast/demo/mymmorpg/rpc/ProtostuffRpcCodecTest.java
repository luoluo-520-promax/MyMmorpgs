/**
 * 文件说明：Protostuff RPC 编解码器单元测试。
 * 职责：验证 RpcRequest 的二进制序列化与反序列化往返一致性。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtostuffRpcCodecTest {

    private static final Logger log = LoggerFactory.getLogger(ProtostuffRpcCodecTest.class);

    @Test
    public void serializeAndDeserialize_roundTrip() {
        long requestId = 42L;
        String serviceClass = "cn.itcast.demo.mymmorpg.rpc.RpcService";
        String methodName = "invoke";
        String[] argTypes = new String[]{"java.lang.String", "int"};
        byte[] argsPayload = new byte[]{10, 20, 30};
        log.info("[测试开始] 场景=Protostuff往返 | requestId={} | serviceClass={} | methodName={} | argTypesLen={} | payloadLen={}",
                requestId, serviceClass, methodName, argTypes.length, argsPayload.length);

        RpcRequest original = new RpcRequest();
        original.setRequestId(requestId);
        original.setServiceClass(serviceClass);
        original.setMethodName(methodName);
        original.setArgTypes(argTypes);
        original.setArgsPayload(argsPayload);

        byte[] bytes = ProtostuffRpcCodec.serialize(original, RpcRequest.class);
        RpcRequest restored = ProtostuffRpcCodec.deserialize(bytes, RpcRequest.class);

        log.info("[测试断言] 场景=Protostuff往返 | bytesLen={} | restoredRequestId={} | restoredMethod={}",
                bytes.length, restored.getRequestId(), restored.getMethodName());
        assertThat(restored.getRequestId()).isEqualTo(requestId);
        assertThat(restored.getServiceClass()).isEqualTo(serviceClass);
        assertThat(restored.getMethodName()).isEqualTo(methodName);
        assertThat(restored.getArgTypes()).containsExactly(argTypes);
        assertThat(restored.getArgsPayload()).containsExactly(argsPayload);
    }

    @Test
    public void deserialize_emptyData_returnsNull() {
        log.info("[测试开始] 场景=空字节反序列化 | data=null");

        RpcRequest restored = ProtostuffRpcCodec.deserialize(null, RpcRequest.class);

        log.info("[测试断言] 场景=空字节反序列化 | restored={}", restored);
        assertThat(restored).isNull();
    }
}
