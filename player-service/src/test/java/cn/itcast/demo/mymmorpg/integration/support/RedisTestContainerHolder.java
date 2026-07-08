/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/test/java/cn/itcast/demo/mymmorpg/integration/support/RedisTestContainerHolder.java
 * 2) 所属模块：player-service / test/java/cn/itcast/demo/mymmorpg/integration/support
 * 3) 主要职责：测试类 RedisTestContainerHolder，验证相关业务逻辑。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.integration.support;

import org.testcontainers.DockerClientFactory; // Docker 检?
import org.testcontainers.containers.GenericContainer; // Redis 容器
import org.testcontainers.utility.DockerImageName; // 镜像?
import redis.embedded.RedisServer; // embedded 回退

import java.io.IOException;
import java.net.ServerSocket; // 空闲端口

/**
 * 玩家服务集成测试?Redis：优?Testcontainers（需 Docker）；本机?Docker 时回退 embedded-redis?
 */
public final class RedisTestContainerHolder { // 工具?

    private static volatile GenericContainer<?> redisContainer; // Docker Redis
    private static volatile RedisServer embeddedRedis; // embedded 实例
    private static volatile boolean started; // 幂等标志

    private RedisTestContainerHolder() { // 禁止实例?
    }

    public static void ensureStarted() { // PlayerServiceApplicationIT static 块调?
        if (started) {
            return;
        }
        synchronized (RedisTestContainerHolder.class) {
            if (started) {
                return;
            }
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    try {
                        startTestcontainersRedis();
                    } catch (RuntimeException e) {
                        startEmbeddedRedis();
                    }
                } else {
                    startEmbeddedRedis();
                }
            } catch (IOException e) {
                throw new IllegalStateException("无法为集成测试启?Redis", e); // 抛出业务异常
            }
            Runtime.getRuntime().addShutdownHook(new Thread(RedisTestContainerHolder::stopQuietly, "redis-it-shutdown"));
            started = true;
        }
    }

    private static void startTestcontainersRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
        System.setProperty("spring.data.redis.host", redisContainer.getHost());
        System.setProperty("spring.data.redis.port", redisContainer.getMappedPort(6379).toString());
    }

    private static void startEmbeddedRedis() throws IOException {
        int port = freePort();
        embeddedRedis = RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1")
                .setting("daemonize no")
                .setting("maxmemory 128M")
                .build(); // 完成 Protobuf 消息构建
        embeddedRedis.start();
        System.setProperty("spring.data.redis.host", "127.0.0.1");
        System.setProperty("spring.data.redis.port", String.valueOf(port));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void stopQuietly() {
        try {
            if (redisContainer != null && redisContainer.isRunning()) {
                redisContainer.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            if (embeddedRedis != null && embeddedRedis.isActive()) {
                embeddedRedis.stop();
            }
        } catch (Exception ignored) {
        }
    }
}
