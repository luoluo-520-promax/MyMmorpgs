/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/integration/support/RedisTestContainerHolder.java
 * 2) 所属模块：activity-service / test / integration / support
 * 3) 主要职责：集成测试 Redis 生命周期——优先 Testcontainers，无 Docker 时回退 embedded-redis
 * 4) 系统位置：被 ActivityServiceApplicationIT 等集成测试 static 块调用
 * 5) 风险提示：两种方式均失败时抛出 IllegalStateException
 */
package cn.itcast.demo.mymmorpg.service.integration.support;

import org.testcontainers.DockerClientFactory; // 检测 Docker 是否可用
import org.testcontainers.containers.GenericContainer; // 通用容器（Redis）
import org.testcontainers.utility.DockerImageName; // 镜像名解析
import redis.embedded.RedisServer; // 无 Docker 时的 embedded-redis

import java.io.IOException; // embedded 启动 IO 异常
import java.net.ServerSocket; // 选取空闲端口

/**
 * 集成测试用 Redis：优先 Testcontainers（需 Docker）；本机无 Docker 时回退 embedded-redis。
 */
public final class RedisTestContainerHolder { // 工具类，禁止实例化

    /** Docker Redis 容器引用。 */
    private static volatile GenericContainer<?> redisContainer; // Testcontainers 容器实例
    /** embedded-redis 实例。 */
    private static volatile RedisServer embeddedRedis; // 嵌入式 Redis 进程
    /** 是否已启动（幂等标志）。 */
    private static volatile boolean started; // 防止重复启动

    /**
     * 私有构造器，禁止实例化。
     */
    private RedisTestContainerHolder() { // 私有构造
    }

    /**
     * 集成测试 static 块调用：保证 Redis 就绪并设置 spring.data.redis.* 系统属性。
     */
    public static void ensureStarted() { // 集成测试 static 块调用：保证 Redis 就绪
        if (started) { // 快速路径：已启动
            return; // 直接返回
        }
        synchronized (RedisTestContainerHolder.class) { // 双检锁
            if (started) { // 再次检查
                return; // 已启动则返回
            }
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) { // 本机有 Docker
                    try {
                        startTestcontainersRedis(); // 启动 redis:7-alpine 并映射端口
                    } catch (RuntimeException e) { // 容器启动失败（权限、镜像等）
                        startEmbeddedRedis(); // 降级 embedded
                    }
                } else {
                    startEmbeddedRedis(); // 无 Docker 直接 embedded
                }
            } catch (IOException e) {
                throw new IllegalStateException("无法为集成测试启动 Redis", e); // 两种方式都失败
            }
            Runtime.getRuntime().addShutdownHook(new Thread(RedisTestContainerHolder::stopQuietly, "redis-it-shutdown")); // JVM 退出时 stop
            started = true; // 标记完成
        }
    }

    /**
     * 使用 Testcontainers 启动 Docker Redis 并设置连接属性。
     */
    private static void startTestcontainersRedis() { // Docker 方式
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")) // 官方轻量镜像
                .withExposedPorts(6379); // 暴露 6379
        redisContainer.start(); // 启动容器
        System.setProperty("spring.data.redis.host", redisContainer.getHost()); // 供 Spring 读取 host
        System.setProperty("spring.data.redis.port", redisContainer.getMappedPort(6379).toString()); // 映射后的宿主机端口
    }

    /**
     * 使用 embedded-redis 在随机端口启动并设置连接属性。
     */
    private static void startEmbeddedRedis() throws IOException { // embedded 方式
        int port = freePort(); // 随机空闲端口，避免与本地 6379 冲突
        embeddedRedis = RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1") // 仅本机
                .setting("daemonize no") // 前台，由 JVM 管理
                .setting("maxmemory 128M") // 测试限制内存
                .build(); // 构建 embedded 实例
        embeddedRedis.start(); // 启动 embedded Redis
        System.setProperty("spring.data.redis.host", "127.0.0.1"); // 本机 host
        System.setProperty("spring.data.redis.port", String.valueOf(port)); // 随机端口
    }

    /**
     * 向 OS 申请临时空闲端口。
     *
     * @return 可用端口号
     */
    private static int freePort() throws IOException { // 向 OS 申请临时端口
        try (ServerSocket socket = new ServerSocket(0)) { // 端口 0 由系统分配
            return socket.getLocalPort(); // 返回分配的端口
        }
    }

    /**
     * ShutdownHook 回调：静默释放 Redis 资源。
     */
    private static void stopQuietly() { // ShutdownHook：静默释放资源
        try {
            if (redisContainer != null && redisContainer.isRunning()) { // Docker 容器在运行
                redisContainer.stop(); // 停止容器
            }
        } catch (Exception ignored) { // 忽略停止异常
        }
        try {
            if (embeddedRedis != null && embeddedRedis.isActive()) { // embedded 在运行
                embeddedRedis.stop(); // 停止 embedded
            }
        } catch (Exception ignored) { // 忽略停止异常
        }
    }
}
