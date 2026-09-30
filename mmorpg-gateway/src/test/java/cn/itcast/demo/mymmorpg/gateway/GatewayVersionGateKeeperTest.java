package cn.itcast.demo.mymmorpg.gateway;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gateway 版本门控：硬拦截、软屏蔽、预热标记。
 */
public class GatewayVersionGateKeeperTest {

    private GatewayVersionGateKeeper gate;
    private Map<String, String> kv;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        kv = new ConcurrentHashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.hasKey(anyString())).thenAnswer(inv -> kv.containsKey(inv.getArgument(0)));
        doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any());
        gate = new GatewayVersionGateKeeper(redis);
        gate.setMinSupportedVersion(10000L);
        gate.setCurrentProtocolHash("proto-v2-current");
    }

    @Test
    public void evaluateLogin_hardRejectBelowMin() {
        GatewayVersionGateKeeper.GateResult r = gate.evaluateLogin(9000, "proto-v1");
        assertThat(r.hardReject()).isTrue();
        assertThat(r.retCode()).isEqualTo(RetCode.CLIENT_TOO_OLD);
    }

    @Test
    public void evaluateLogin_softBlockKnownOldProtocol() {
        GatewayVersionGateKeeper.GateResult r = gate.evaluateLogin(15000, "proto-v1");
        assertThat(r.hardReject()).isFalse();
        assertThat(r.blockedMsgIds()).contains(2600, 2605);
    }

    @Test
    public void evaluateLogin_hardRejectCombatProtocol() {
        GatewayVersionGateKeeper.GateResult r = gate.evaluateLogin(16000, "proto-combat-v2");
        assertThat(r.hardReject()).isTrue();
        assertThat(r.retCode()).isEqualTo(RetCode.CLIENT_TOO_OLD);
    }

    @Test
    public void preheat_markAndDetect() {
        assertThat(gate.isPreheatActive("2.6.0")).isFalse();
        gate.markPreheat("2.6.0");
        assertThat(gate.isPreheatActive("2.6.0")).isTrue();
        assertThat(kv).containsKey("version:preheat:2.6.0");
    }
}
