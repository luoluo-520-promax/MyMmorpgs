package cn.itcast.demo.mymmorpg.test.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 单元/流程测试用 Redis：embedded-redis + Lettuce StringRedisTemplate。
 */
public final class RedisITSupport implements AutoCloseable {

    private final RedisServer server;
    private final LettuceConnectionFactory factory;
    private final StringRedisTemplate template;

    private RedisITSupport(RedisServer server, LettuceConnectionFactory factory, StringRedisTemplate template) {
        this.server = server;
        this.factory = factory;
        this.template = template;
    }

    public static RedisITSupport start() {
        try {
            int port = freePort();
            RedisServer server = RedisServer.newRedisServer()
                    .port(port)
                    .setting("bind 127.0.0.1")
                    .setting("daemonize no")
                    .setting("maxmemory 64M")
                    .build();
            server.start();
            RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration("127.0.0.1", port);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(conf);
            factory.afterPropertiesSet();
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            return new RedisITSupport(server, factory, template);
        } catch (IOException e) {
            throw new IllegalStateException("embedded redis start failed", e);
        }
    }

    public StringRedisTemplate redis() {
        return template;
    }

    @Override
    public void close() {
        try {
            factory.destroy();
        } catch (Exception ignored) {
        }
        try {
            if (server != null && server.isActive()) {
                server.stop();
            }
        } catch (Exception ignored) {
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
