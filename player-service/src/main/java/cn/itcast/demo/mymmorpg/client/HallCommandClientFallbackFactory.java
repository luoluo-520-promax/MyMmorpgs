package cn.itcast.demo.mymmorpg.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * hall-service 熔断降级：好友/邮件/排行短暂不可用时返回空负载。
 */
@Component
public class HallCommandClientFallbackFactory implements FallbackFactory<HallCommandClient> {

    private static final Logger log = LoggerFactory.getLogger(HallCommandClientFallbackFactory.class);

    @Override
    public HallCommandClient create(Throwable cause) {
        return new HallCommandClient() {
            private void warn(String op) {
                log.warn("hall-service degraded op={} cause={}", op,
                        cause == null ? "unknown" : cause.toString());
            }

            @Override
            public byte[] friends(long playerId, byte[] body) {
                warn("friends");
                return new byte[0];
            }

            @Override
            public byte[] addFriend(long playerId, byte[] body) {
                warn("addFriend");
                return new byte[0];
            }

            @Override
            public byte[] mails(long playerId, byte[] body) {
                warn("mails");
                return new byte[0];
            }

            @Override
            public byte[] claimMail(long playerId, byte[] body) {
                warn("claimMail");
                return new byte[0];
            }

            @Override
            public byte[] ranking(long playerId, byte[] body) {
                warn("ranking");
                return new byte[0];
            }
        };
    }
}
