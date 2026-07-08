/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/EmbeddedRedisDevSupport.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：本地开发时在 6379 空闲则启动 embedded-redis，免 Docker 依赖 auth/token 与 Spring Cache。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.slf4j.Logger; // 记录 skip/start/fail 便于本地排查端口占用
import org.slf4j.LoggerFactory; // 按类名创建 Logger
import redis.embedded.RedisServer; // embedded-redis 库封装的本地 Redis 进程
import java.io.IOException; // redisServer.start() 可能因端口/bind 失败
import java.net.InetSocketAddress; // TCP connect 探测 host:port
import java.net.Socket; // 短连接 PING 验证 Redis 协议
/**
 * 本地开发用嵌入式 Redis：6379 未被占用时自动启动，避免强依赖 Docker。
 * 由 {@link EmbeddedRedisDevEnvironmentPostProcessor} 与 {@link cn.itcast.demo.mymmorpg.MyMmorpgApplication#main} 共同调用。
 */

public final class EmbeddedRedisDevSupport { // 工具类，禁止实例化
    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisDevSupport.class); // SLF4J Logger，记录本类 Netty/WebSocket/dispatch 日志
    /** 进程内单例 embedded 实例，main 与 EnvironmentPostProcessor 双入口共享，避免重复 start */

    private static volatile RedisServer redisServer; // EmbeddedRedisDevSupport 字段
    private EmbeddedRedisDevSupport() { // 私有构造，强制静态 API
    } // 编译单元结束
    /**
     * 按开关与端口探测结果决定是否启动 embedded-redis。
     *
     * @param enabled game.dev.embedded-redis.enabled 或 EMBEDDED_REDIS_ENABLED
     * @param host    与 spring.data.redis.host 一致
     * @param port    与 spring.data.redis.port 一致，通常 6379
     */

    public static void startIfNeeded(boolean enabled, String host, int port) { // EmbeddedRedisDevSupport.startIfNeeded：boolean enabled, String host, int port
        if (!enabled) { // 显式关闭 embedded，连接外部 Redis 集群
            return; // EmbeddedRedisDevSupport 逻辑
        } // startIfNeeded 方法体结束
        if (isRedisAvailable(host, port)) { // 已有 Redis 监听且 RESP PING 成功
            log.info("skip embedded redis, {}:{} already available", host, port); // 记录 dev 种子/路由注册/Netty 启停日志
            return; // EmbeddedRedisDevSupport 逻辑
        } // 编译单元结束
        synchronized (EmbeddedRedisDevSupport.class) { // 双检锁：并发 main + PostProcessor 只 start 一次
            if (redisServer != null && redisServer.isActive()) { // EmbeddedRedisDevSupport 方法
                return; // EmbeddedRedisDevSupport 逻辑
            } // if 方法体结束
            try { // 代码块开始
                // embedded-redis 不支持 bind 0.0.0.0，转为 127.0.0.1 本地监听
                String bindHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host; // embedded-redis bind 127.0.0.1 替代 0.0.0.0
                redisServer = RedisServer.newRedisServer() // EmbeddedRedisDevSupport 逻辑
                        .port(port) // 与 application.yml spring.data.redis.port 对齐
                        .setting("bind " + bindHost) // redis.conf bind 指令
                        .setting("daemonize no") // 子进程由 JVM 管理，非 Unix daemon
                        .setting("maxmemory 128M") // 开发环境限制内存，防 OOM
                        .build(); // EmbeddedRedisDevSupport 逻辑
                redisServer.start(); // 拉起 embedded Redis 子进程
                awaitPort(bindHost, port, 5_000); // 最多 5s 等待 PING 就绪
                Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedRedisDevSupport::stopQuietly, "embedded-redis-shutdown")); // JVM 退出 hook，stop embedded Redis 释放 6379
                log.info("embedded redis started on {}:{}", bindHost, port); // 记录 dev 种子/路由注册/Netty 启停日志
            } catch (IOException | RuntimeException e) { // EmbeddedRedisDevSupport.catch：IOException | RuntimeException e
                throw new IllegalStateException("failed to start embedded redis on " + host + ":" + port, e); // 配置/路由错误快速失败
            } // catch 方法体结束
        } // 块 代码块结束
    } // 编译单元结束
    /** 轮询 isRedisAvailable 直至成功或超时，避免 Spring 在 Redis 未就绪时连接失败 */

    private static void awaitPort(String host, int port, long timeoutMs) { // EmbeddedRedisDevSupport.awaitPort：String host, int port, long timeoutMs
        long deadline = System.currentTimeMillis() + timeoutMs; // 活动起止时间毫秒戳
        while (System.currentTimeMillis() < deadline) { // EmbeddedRedisDevSupport 方法
            if (isRedisAvailable(host, port)) { // EmbeddedRedisDevSupport 方法
                return; // EmbeddedRedisDevSupport 逻辑
            } // if 方法体结束
            try { // 代码块开始
                Thread.sleep(100); // 100ms 间隔，降低 CPU 空转
            } catch (InterruptedException e) { // EmbeddedRedisDevSupport.catch：InterruptedException e
                Thread.currentThread().interrupt(); // 恢复中断标志
                throw new IllegalStateException("interrupted while waiting for embedded redis on " + host + ":" + port, e); // 配置/路由错误快速失败
            } // catch 方法体结束
        } // 块 代码块结束
        throw new IllegalStateException("embedded redis did not become ready on " + host + ":" + port + " within " + timeoutMs + "ms"); // 配置/路由错误快速失败
    } // while 方法体结束
    /** 仅探测 TCP 端口是否可连接，不验证 Redis 协议（可能是其他服务占用） */

    private static boolean isPortOpen(String host, int port) { // 判断 PortOpen 是否为真
        try (Socket socket = new Socket()) { // TCP 连接后发 RESP PING 验证 Redis 协议
            socket.connect(new InetSocketAddress(host, port), 500); // 500ms 连接超时
            return true; // 校验通过
        } catch (IOException e) { // EmbeddedRedisDevSupport.catch：IOException e
            return false; // 校验失败返回 false
        } // catch 方法体结束
    } // isPortOpen 方法体结束
    /** 端口可达且 RESP PING 返回 PONG，避免把 MySQL/其他 TCP 服务误判为 Redis */

    private static boolean isRedisAvailable(String host, int port) { // 判断 RedisAvailable 是否为真
        if (!isPortOpen(host, port)) { // EmbeddedRedisDevSupport 方法
            return false; // 校验失败返回 false
        } // if 方法体结束
        try (Socket socket = new Socket()) { // TCP 连接后发 RESP PING 验证 Redis 协议
            socket.connect(new InetSocketAddress(host, port), 500); // 500ms 超时 TCP 连接 host:port
            socket.getOutputStream().write("PING\r\n".getBytes()); // RESP 简单 PING 命令
            socket.getOutputStream().flush(); // 刷新 TCP 输出确保 PING 发出
            byte[] buffer = new byte[64]; // RESP PING 响应读缓冲，最多 64 字节
            int read = socket.getInputStream().read(buffer); // 读取 RESP 响应判断 PONG
            if (read <= 0) { // EmbeddedRedisDevSupport.if：read <= 0
                return false; // 校验失败返回 false
            } // if 方法体结束
            String response = new String(buffer, 0, read); // 解码 RESP 响应为字符串查 PONG
            return response.startsWith("+PONG") || response.contains("PONG"); // 兼容不同 embedded 响应格式
        } catch (IOException e) { // EmbeddedRedisDevSupport.catch：IOException e
            return false; // 校验失败返回 false
        } // catch 方法体结束
    } // isRedisAvailable 方法体结束
    /** JVM shutdown hook：进程退出时 stop embedded，释放 6379 端口 */

    private static void stopQuietly() { // EmbeddedRedisDevSupport.stopQuietly：无参
        try { // 代码块开始
            if (redisServer != null && redisServer.isActive()) { // EmbeddedRedisDevSupport 方法
                redisServer.stop(); // stop embedded Redis 释放端口
            } // if 方法体结束
        } catch (Exception ignored) { // EmbeddedRedisDevSupport.catch：Exception ignored
            // 退出阶段不抛异常，避免阻断其他 hook
        } // catch 方法体结束
    } // 块 代码块结束
} // stopQuietly 方法体结束
