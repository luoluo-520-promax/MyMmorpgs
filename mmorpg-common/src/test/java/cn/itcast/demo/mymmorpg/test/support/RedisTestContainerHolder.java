package cn.itcast.demo.mymmorpg.test.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 集成测试 Redis 支撑：优先 Testcontainers，回退 embedded-redis。
 */
public final class RedisTestContainerHolder {

    private static volatile GenericContainer<?> redisContainer;
    private static volatile RedisServer embeddedRedis;
    private static volatile boolean started;

    private RedisTestContainerHolder() {
    }

    public static void ensureStarted() {
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
                throw new IllegalStateException("无法为集成测试启动 Redis", e);
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
                .build();
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
