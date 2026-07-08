/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RequestResponseFuture.java
 * 类型：类
 * 职责：同步等待跨服 RPC 响应的 Future 封装。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 同步等待跨服 RPC 响应的 Future。
 * <p>
 * {@link RpcClient#send} 发出请求后注册到 {@link CallBackService}，
 * 业务线程调用 {@link #await(long)} 阻塞直至战斗服/中心服回包或超时。
 * </p>
 */
public class RequestResponseFuture<T> {

    /** 与 RpcForwardClientMessage.requestId 对应，用于匹配异步回包 */
    private final long requestId;

    /** 初始计数 1：收到 setResult 时 countDown，唤醒 await 中的业务线程 */
    private final CountDownLatch latch = new CountDownLatch(1);

    /** 战斗服/中心服返回的结果，由 Netty IO 线程写入，业务线程读取需 volatile */
    private volatile T result;

    /**
     * @param requestId 本次跨服 RPC 的唯一请求号
     */
    public RequestResponseFuture(long requestId) {
        this.requestId = requestId; // 供 CallBackService.complete 按 ID 查找
    }

    /**
     * @return 关联的 requestId，便于日志与超时排查
     */
    public long getRequestId() {
        return requestId;
    }

    /**
     * Netty 入站线程在收到 {@link RpcForwardClientResponse} 等回包时调用，唤醒等待方。
     *
     * @param result 反序列化后的 RPC 响应体
     */
    public void setResult(T result) {
        this.result = result; // 写入响应结果
        latch.countDown(); // 释放 await 中的 CountDownLatch，完成一次请求-响应配对
    }

    /**
     * 业务线程阻塞等待跨服 RPC 回包。
     *
     * @param timeoutMs 最大等待毫秒数（如转发客户端战斗指令到战斗服）
     * @return 回包结果；超时未收到则返回 null
     * @throws InterruptedException 等待被中断
     */
    public T await(long timeoutMs) throws InterruptedException {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) { // 超时：战斗服无响应或网络异常
            return null;
        }
        return result; // 正常收到 CallBackService.complete 设置的响应
    }
}
