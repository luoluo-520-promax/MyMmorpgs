package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.version.VersionGateKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录态客户端版本治理：委托 VersionGateKeeper 做协议 Hash 双校验。
 */
@Service
public class ClientVersionGate {

    private final VersionGateKeeper versionGateKeeper;
    private long minClientVersion;
    private long optionalClientVersion;

    public ClientVersionGate(VersionGateKeeper versionGateKeeper) {
        this.versionGateKeeper = versionGateKeeper;
    }

    /** 无 Spring 容器时的测试/嵌入构造。 */
    public ClientVersionGate() {
        this(null);
    }

    @Value("${game.client.min-version:0}")
    void setMinClientVersion(long minClientVersion) {
        this.minClientVersion = Math.max(0L, minClientVersion);
        if (versionGateKeeper != null) {
            versionGateKeeper.compatMap().setMinSupportedVersion(this.minClientVersion);
        }
    }

    @Value("${game.client.optional-version:0}")
    void setOptionalClientVersion(long optionalClientVersion) {
        this.optionalClientVersion = Math.max(0L, optionalClientVersion);
    }

    public record GateResult(int retCode, boolean recommendUpdate) {
        public static GateResult ok(boolean recommend) {
            return new GateResult(RetCode.OK, recommend);
        }

        public static GateResult reject(int code) {
            return new GateResult(code, false);
        }
    }

    public GateResult evaluate(long clientVersionNumber) {
        return evaluate(clientVersionNumber, "", 0L);
    }

    public GateResult evaluate(long clientVersionNumber, String protocolSchemaHash, long accountId) {
        if (versionGateKeeper != null) {
            VersionGateKeeper.GateResult gate =
                    versionGateKeeper.evaluateLogin(clientVersionNumber, protocolSchemaHash, accountId);
            if (gate.hardReject()) {
                return GateResult.reject(gate.retCode());
            }
            return GateResult.ok(gate.recommendUpdate());
        }
        if (minClientVersion > 0 && clientVersionNumber < minClientVersion) {
            return GateResult.reject(RetCode.CLIENT_VERSION_TOO_OLD);
        }
        boolean recommend = optionalClientVersion > 0 && clientVersionNumber < optionalClientVersion;
        return GateResult.ok(recommend);
    }
}
