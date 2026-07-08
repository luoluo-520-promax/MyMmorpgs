/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/DispatchThreadModel.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 DispatchThreadModel，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import jakarta.annotation.PreDestroy; // 容器关闭时优雅停止 dispatch 线程池
import org.springframework.beans.factory.annotation.Value; // game.dispatch.stripes 分片数，0 表示按 CPU 核数
import org.springframework.stereotype.Component; // MessageDispatchPipeline 注入，Netty/WebSocket 共用
import java.util.concurrent.ExecutorService; // 分片单线程池数组，每片串行执行
import java.util.concurrent.Executors; // 创建单线程 Executor，保证同 key 消息顺序
import java.util.concurrent.ThreadFactory; // 命名 dispatch-N-M 线程，便于 jstack 排查
import java.util.concurrent.TimeUnit; // shutdown 等待超时 3 秒
import java.util.concurrent.atomic.AtomicInteger; // 线程名自增序号
/**
 * 业务线程分片模型：dispatchKey（通常 playerId）映射到固定 stripe，同玩家消息严格串行、不同玩家并行。
 */
@Component // Spring 单例，MessageDispatchPipeline 按 dispatchKey submit ClientRequestTask
public class DispatchThreadModel { // playerId 或 marker.hashCode 映射 stripe，同 key FIFO 串行
    /** 分片线程池数组，长度即并行度；每个元素是单线程 Executor 保证片内 FIFO */
    private final ExecutorService[] stripes; // stripes[i] 单线程池，同 dispatchKey 始终落同一 i
    public DispatchThreadModel(@Value("${game.dispatch.stripes:0}") int stripes) { // game.dispatch.stripes 配置业务分片数
        int n = stripes > 0 ? stripes : Math.max(2, Runtime.getRuntime().availableProcessors()); // 0 时默认 CPU 核数，至少 2 片
        this.stripes = new ExecutorService[n]; // 分配 n 个单线程 Executor 槽位
        for (int i = 0; i < n; i++) { // 逐片创建单线程池
            this.stripes[i] = Executors.newSingleThreadExecutor(new NamedFactory("dispatch-" + i)); // dispatch-i 线程串行执行同片任务
        } // 编译单元结束
    } // 编译单元结束

    /** 按 dispatchKey 取模选 stripe，将 ClientRequestTask 或 RPC 转发任务提交到对应单线程池 */
    public void submit(long dispatchKey, Runnable task) { // MessageDispatchPipeline 提交 ClientRequestTask 或 RPC lambda
        if (task == null) { // 防御空 Runnable
            return; // 忽略无效提交
        } // 编译单元结束

        int idx = (int) (Math.floorMod(dispatchKey, stripes.length)); // 负数 playerId 也能正确取模到 [0,n)
        stripes[idx].submit(task); // 异步执行，Netty/WebSocket IO 线程立即返回继续读帧
    } // 编译单元结束

    @PreDestroy // 容器关闭前释放资源
    public void shutdown() { // Spring 容器关闭时调用
        for (ExecutorService es : stripes) { // 第一阶段：停止接收新 dispatch 任务
            es.shutdown(); // 不再 accept 新 ClientRequestTask
        } // 编译单元结束

        for (ExecutorService es : stripes) { // 等待已在队列中的消息处理完
            try { // 代码块开始
                es.awaitTermination(3, TimeUnit.SECONDS); // 最多等 3s 让在途 Facade 执行完毕
            } catch (InterruptedException e) { // 等待被中断
                Thread.currentThread().interrupt(); // 恢复中断标志，供上层感知
            } // 块 代码块结束
        } // 编译单元结束

        for (ExecutorService es : stripes) { // 超时仍未结束则强制中断
            es.shutdownNow(); // 中断阻塞中的 RPC await 或 Facade 逻辑
        } // 编译单元结束
    } // 编译单元结束

    /** 为 dispatch stripe 线程命名 dispatch-{stripeIdx}-{seq}，daemon 避免阻塞 JVM 退出 */
    private static final class NamedFactory implements ThreadFactory { // 每 stripe 独立 ThreadFactory
        private final String prefix; // 如 "dispatch-0"
        private final AtomicInteger seq = new AtomicInteger(); // 同 stripe 内线程序号自增
        private NamedFactory(String prefix) { // stripe 索引作为前缀
            this.prefix = prefix; // 记录线程名前缀 dispatch-N
        } // 编译单元结束

        @Override // 实现接口/父类方法
        public Thread newThread(Runnable r) { // Executors.newSingleThreadExecutor 回调
            Thread t = new Thread(r); // 创建 dispatch stripe 工作线程
            t.setName(prefix + "-" + seq.incrementAndGet()); // jstack 可见 dispatch-0-1 等名称
            t.setDaemon(true); // 守护线程，Spring Boot 退出时不阻塞 JVM
            return t; // 交 Executor 启动 stripe 工作线程
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
