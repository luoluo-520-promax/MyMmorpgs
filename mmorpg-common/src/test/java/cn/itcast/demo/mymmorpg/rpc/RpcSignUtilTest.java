/**
 * 文件说明：跨服 RPC 握手签名工具单元测试。
 * 职责：验证 RpcSignUtil 的 sign / verify 算法与边界条件。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcSignUtilTest {

    private static final Logger log = LoggerFactory.getLogger(RpcSignUtilTest.class);

    @Test
    public void sign_generatesExpectedMd5() {
        int serverId = 1001;
        String signKey = "mmorpg-test-key";
        log.info("[测试开始] 场景=生成握手签名 | serverId={} | signKey={}", serverId, signKey);

        String sign = RpcSignUtil.sign(serverId, signKey);

        log.info("[测试断言] 场景=生成握手签名 | sign={} | 长度期望=32", sign);
        assertThat(sign).hasSize(32);
        assertThat(sign).isEqualTo(RpcSignUtil.sign(serverId, signKey));
    }

    @Test
    public void verify_acceptsValidSign() {
        int serverId = 2002;
        String signKey = "cluster-shared-key";
        String sign = RpcSignUtil.sign(serverId, signKey);
        log.info("[测试开始] 场景=校验合法签名 | serverId={} | signKey={} | sign={}",
                serverId, signKey, sign);

        boolean ok = RpcSignUtil.verify(signKey, serverId, sign);

        log.info("[测试断言] 场景=校验合法签名 | ok={} | 期望=true", ok);
        assertThat(ok).isTrue();
    }

    @Test
    public void verify_rejectsInvalidSign() {
        int serverId = 3003;
        String signKey = "cluster-shared-key";
        String wrongSign = "deadbeefdeadbeefdeadbeefdeadbeef";
        log.info("[测试开始] 场景=校验非法签名 | serverId={} | signKey={} | wrongSign={}",
                serverId, signKey, wrongSign);

        boolean ok = RpcSignUtil.verify(signKey, serverId, wrongSign);

        log.info("[测试断言] 场景=校验非法签名 | ok={} | 期望=false", ok);
        assertThat(ok).isFalse();
    }

    @Test
    public void verify_rejectsNullParams() {
        log.info("[测试开始] 场景=空参验签 | signKey=null | serverId=1 | sign=null");

        boolean ok = RpcSignUtil.verify(null, 1, null);

        log.info("[测试断言] 场景=空参验签 | ok={} | 期望=false", ok);
        assertThat(ok).isFalse();
    }
}
