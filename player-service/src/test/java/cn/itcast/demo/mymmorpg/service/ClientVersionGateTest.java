package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录客户端版本门禁：MIN 强制拦截，OPTIONAL 仅推荐更新。
 */
public class ClientVersionGateTest {

    @Test
    public void evaluate_rejectsWhenBelowMin() {
        ClientVersionGate gate = new ClientVersionGate();
        gate.setMinClientVersion(10000);
        gate.setOptionalClientVersion(11000);

        ClientVersionGate.GateResult result = gate.evaluate(9000);
        assertThat(result.retCode()).isEqualTo(RetCode.CLIENT_VERSION_TOO_OLD);
        assertThat(result.recommendUpdate()).isFalse();
    }

    @Test
    public void evaluate_recommendsWhenBelowOptionalButAboveMin() {
        ClientVersionGate gate = new ClientVersionGate();
        gate.setMinClientVersion(10000);
        gate.setOptionalClientVersion(12000);

        ClientVersionGate.GateResult result = gate.evaluate(11000);
        assertThat(result.retCode()).isEqualTo(RetCode.OK);
        assertThat(result.recommendUpdate()).isTrue();
    }

    @Test
    public void evaluate_okWhenAboveOptional() {
        ClientVersionGate gate = new ClientVersionGate();
        gate.setMinClientVersion(10000);
        gate.setOptionalClientVersion(12000);

        ClientVersionGate.GateResult result = gate.evaluate(13000);
        assertThat(result.retCode()).isEqualTo(RetCode.OK);
        assertThat(result.recommendUpdate()).isFalse();
    }

    @Test
    public void evaluate_skipsWhenMinDisabled() {
        ClientVersionGate gate = new ClientVersionGate();
        gate.setMinClientVersion(0);
        gate.setOptionalClientVersion(0);

        ClientVersionGate.GateResult result = gate.evaluate(0);
        assertThat(result.retCode()).isEqualTo(RetCode.OK);
        assertThat(result.recommendUpdate()).isFalse();
    }
}
