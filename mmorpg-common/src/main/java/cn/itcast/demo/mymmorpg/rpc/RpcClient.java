/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcClient.java
 * 类型：类
 * 职责：单条跨服 RPC 连接上的请求发送器（同步等待模式）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 绑定到一条 Netty RPC 会话（{@link IdSession}）的请求发送器。
 * <p>
 * 游戏服经 {@link RpcClientRouter} 选中战斗服 Session 后，用本类发送
 * {@link RpcForwardClientMessage} 并同步 await 战斗服回包。
 * </p>
 */
public class RpcClient {

    /** 进程内全局递增 requestId，保证跨服 RPC 请求可唯一匹配回包 */
    private static final AtomicLong REQUEST_ID = new AtomicLong(1);

    /** 底层 Netty 会话，负责 wrap + 写入 RpcLengthJsonEncoder 管道 */
    private final IdSession session;

    /** 注册/完成 RequestResponseFuture，衔接 IO 线程与业务线程 */
    private final CallBackService callBackService;

    /**
     * @param session         已握手成功的战斗服/中心服 RPC 会话
     * @param callBackService Spring 注入的回调注册表
     */
    public RpcClient(IdSession session, CallBackService callBackService) {
        this.session = session; // 实际 TCP 发送出口
        this.callBackService = callBackService; // send 前 register，回包时 complete
    }

    /**
     * 生成下一个跨服 RPC requestId（线程安全递增）。
     *
     * @return 新的 requestId，写入 RpcForwardClientMessage 等请求体
     */
    public long nextRequestId() {
        return REQUEST_ID.incrementAndGet(); // 原子递增，避免并发转发战斗包时 ID 冲突
    }

    /**
     * 发送跨服 RPC 并返回可 await 的 Future。
     *
     * @param requestId 已分配的 requestId（通常来自 nextRequestId）
     * @param request   请求 POJO（经 session 内部 wrap 为 RpcWireEnvelope 后发出）
     * @return 业务线程可 await 的 Future，超时或回包后得到结果
     */
    public <T> RequestResponseFuture<T> send(long requestId, Object request) {
        RequestResponseFuture<T> future = new RequestResponseFuture<>(requestId); // 创建与 requestId 绑定的等待器
        callBackService.register(requestId, future); // 注册到 pending 表，供 Netty 回包线程 complete
        session.send(request); // 经 Netty 管道编码为 [length][json] 发往战斗服/中心服
        return future; // 调用方 await(timeoutMs) 阻塞直至战斗服响应
    }
}
