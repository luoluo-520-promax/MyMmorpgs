/**
 * 文件说明：集成测试 Redis 容器持有者工具类。
 * 职责：为集成测试提供 Redis 实例——优先使用 Testcontainers（需 Docker），
 *       本机无 Docker 时回退 embedded-redis，并设置 spring.data.redis.* 系统属性。
 */
package cn.itcast.demo.mymmorpg.service.integration.support;

import org.testcontainers.DockerClientFactory; // Docker 可用性检测
import org.testcontainers.containers.GenericContainer; // Redis 容器
import org.testcontainers.utility.DockerImageName; // 镜像名
import redis.embedded.RedisServer; // embedded 回退方案

import java.io.IOException; // IO 异常
import java.net.ServerSocket; // 空闲端口探测

/**
 * 集成测试用 Redis：优先 Testcontainers（需 Docker）；本机无 Docker 时回退 embedded-redis。
 */
public final class RedisTestContainerHolder { // Redis 测试工具类

    /** Testcontainers Redis 容器实例 */
    private static volatile GenericContainer<?> redisContainer; // Docker 容器
    /** embedded-redis 实例 */
    private static volatile RedisServer embeddedRedis; // embedded 实例
    /** 是否已启动标志（幂等） */
    private static volatile boolean started; // 幂等标志

    /**
     * 私有构造器，禁止实例化。
     */
    private RedisTestContainerHolder() { // 禁止 new
    }

    /**
     * 保证 Redis 就绪并设置 spring.data.redis.* 系统属性。
     */
    public static void ensureStarted() { // 保证 Redis 就绪
        if (started) { // 已启动
            return; // 直接返回
        }
        synchronized (RedisTestContainerHolder.class) { // 双重检查锁
            if (started) { // 再次检查
                return; // 已启动
            }
            try { // 启动 Redis
                if (DockerClientFactory.instance().isDockerAvailable()) { // Docker 可用
                    try { // 尝试 Testcontainers
                        startTestcontainersRedis(); // 启动容器 Redis
                    } catch (RuntimeException e) { // 容器启动失败
                        startEmbeddedRedis(); // 回退 embedded
                    }
                } else { // Docker 不可用
                    startEmbeddedRedis(); // 直接用 embedded
                }
            } catch (IOException e) { // 启动 IO 异常
                throw new IllegalStateException("无法为集成测试启动 Redis", e); // 抛运行时异常
            }
            Runtime.getRuntime().addShutdownHook(new Thread(RedisTestContainerHolder::stopQuietly, "redis-it-shutdown")); // 注册关闭钩子
            started = true; // 标记已启动
        }
    }

    /**
     * 使用 Testcontainers 启动 Redis 容器。
     */
    private static void startTestcontainersRedis() { // 启动 Docker Redis
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")) // 使用 redis:7-alpine 镜像
                .withExposedPorts(6379); // 暴露 6379 端口
        redisContainer.start(); // 启动容器
        System.setProperty("spring.data.redis.host", redisContainer.getHost()); // 设置 Redis 主机
        System.setProperty("spring.data.redis.port", redisContainer.getMappedPort(6379).toString()); // 设置映射端口
    }

    /**
     * 使用 embedded-redis 启动本地 Redis。
     */
    private static void startEmbeddedRedis() throws IOException { // 启动 embedded Redis
        int port = freePort(); // 获取空闲端口
        embeddedRedis = RedisServer.newRedisServer() // 创建 embedded 服务
                .port(port) // 绑定端口
                .setting("bind 127.0.0.1") // 仅本地绑定
                .setting("daemonize no") // 非守护进程
                .setting("maxmemory 128M") // 限制内存
                .build(); // 构建
        embeddedRedis.start(); // 启动
        System.setProperty("spring.data.redis.host", "127.0.0.1"); // 设置主机
        System.setProperty("spring.data.redis.port", String.valueOf(port)); // 设置端口
    }

    /**
     * 获取系统空闲端口。
     *
     * @return 可用端口号
     */
    private static int freePort() throws IOException { // 获取空闲端口
        try (ServerSocket socket = new ServerSocket(0)) { // 绑定端口 0 由系统分配
            return socket.getLocalPort(); // 返回分配的端口
        }
    }

    /**
     * 安静关闭 Redis 实例（供 ShutdownHook 调用）。
     */
    private static void stopQuietly() { // 安静关闭
        try { // 关闭容器
            if (redisContainer != null && redisContainer.isRunning()) { // 容器在运行
                redisContainer.stop(); // 停止容器
            }
        } catch (Exception ignored) { // 忽略异常
        }
        try { // 关闭 embedded
            if (embeddedRedis != null && embeddedRedis.isActive()) { // embedded 在运行
                embeddedRedis.stop(); // 停止 embedded
            }
        } catch (Exception ignored) { // 忽略异常
        }
    }
}
