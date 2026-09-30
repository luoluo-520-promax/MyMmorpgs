package cn.itcast.demo.mymmorpg.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * scene-service 熔断降级：返回空负载，避免故障级联到玩家网关线程。
 */
@Component
public class SceneCommandClientFallbackFactory implements FallbackFactory<SceneCommandClient> {

    private static final Logger log = LoggerFactory.getLogger(SceneCommandClientFallbackFactory.class);

    @Override
    public SceneCommandClient create(Throwable cause) {
        return new SceneCommandClient() {
            private void warn(String op) {
                log.warn("scene-service degraded op={} cause={}", op,
                        cause == null ? "unknown" : cause.toString());
            }

            @Override
            public byte[] enter(long playerId, byte[] body) {
                warn("enter");
                return new byte[0];
            }

            @Override
            public byte[] cur(long playerId, byte[] body) {
                warn("cur");
                return new byte[0];
            }

            @Override
            public byte[] move(long playerId, byte[] body) {
                warn("move");
                return new byte[0];
            }

            @Override
            public byte[] switchLine(long playerId, byte[] body) {
                warn("switchLine");
                return new byte[0];
            }

            @Override
            public byte[] nearby(long playerId, byte[] body) {
                warn("nearby");
                return new byte[0];
            }

            @Override
            public byte[] transfer(long playerId, byte[] body) {
                warn("transfer");
                return new byte[0];
            }

            @Override
            public byte[] resume(long playerId, byte[] body) {
                warn("resume");
                return new byte[0];
            }

            @Override
            public void leave(long playerId) {
                warn("leave");
            }

            @Override
            public void disconnect(long playerId) {
                warn("disconnect");
            }
        };
    }
}
