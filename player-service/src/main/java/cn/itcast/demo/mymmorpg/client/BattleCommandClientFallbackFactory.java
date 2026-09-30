package cn.itcast.demo.mymmorpg.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * battle-service 熔断降级：战斗链路故障时快速失败，避免拖垮玩家服。
 */
@Component
public class BattleCommandClientFallbackFactory implements FallbackFactory<BattleCommandClient> {

    private static final Logger log = LoggerFactory.getLogger(BattleCommandClientFallbackFactory.class);

    @Override
    public BattleCommandClient create(Throwable cause) {
        return new BattleCommandClient() {
            private void warn(String op) {
                log.warn("battle-service degraded op={} cause={}", op,
                        cause == null ? "unknown" : cause.toString());
            }

            @Override
            public byte[] start(long playerId, byte[] body) {
                warn("start");
                return new byte[0];
            }

            @Override
            public byte[] action(long playerId, byte[] body) {
                warn("action");
                return new byte[0];
            }

            @Override
            public byte[] end(long playerId, byte[] body) {
                warn("end");
                return new byte[0];
            }
        };
    }
}
