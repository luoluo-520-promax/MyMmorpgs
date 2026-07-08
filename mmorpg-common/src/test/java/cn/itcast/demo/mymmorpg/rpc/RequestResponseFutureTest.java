/**
 * 文件说明：RPC 同步等待 Future 单元测试。
 * 职责：验证 RequestResponseFuture 的 setResult / await 配对行为。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestResponseFutureTest {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseFutureTest.class);

    @Test
    public void await_returnsResultWhenSet() throws Exception {
        long requestId = 778899L;
        String resultPayload = "battle-ok";
        log.info("[测试开始] 场景=正常回包 | requestId={} | resultPayload={}", requestId, resultPayload);

        RequestResponseFuture<String> future = new RequestResponseFuture<>(requestId);
        Thread responder = new Thread(() -> {
            try {
                Thread.sleep(50);
                future.setResult(resultPayload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responder.start();

        String result = future.await(2000);

        log.info("[测试断言] 场景=正常回包 | result={} | 期望={}", result, resultPayload);
        assertThat(result).isEqualTo(resultPayload);
        responder.join();
    }

    @Test
    public void await_timeoutReturnsNull() throws Exception {
        long requestId = 112233L;
        long timeoutMs = 100L;
        log.info("[测试开始] 场景=等待超时 | requestId={} | timeoutMs={}", requestId, timeoutMs);

        RequestResponseFuture<String> future = new RequestResponseFuture<>(requestId);
        String result = future.await(timeoutMs);

        log.info("[测试断言] 场景=等待超时 | result={} | 期望=null", result);
        assertThat(result).isNull();
    }
}
