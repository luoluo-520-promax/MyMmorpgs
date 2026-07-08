/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/CallBackService.java
 * 类型：类
 * 职责：管理 requestId 与 {@link RequestResponseFuture} 映射，处理跨服 RPC 异步回包。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 跨服 RPC 回调注册表：requestId → {@link RequestResponseFuture}。
 * <p>
 * 游戏服经 {@link RpcClient} 向战斗服转发客户端战斗包时先 register，
 * Netty 入站 {@link FightOutboundHandler} 收到 {@link RpcForwardClientResponse} 后 complete，
 * 唤醒业务线程的 await。
 * </p>
 */
@Component
public class CallBackService {

    /** 并发安全的待完成 RPC 表：key=requestId，value=等待中的 Future */
    private final ConcurrentMap<Long, RequestResponseFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * 发送跨服 RPC 前注册 Future，以便回包时按 requestId 唤醒。
     *
     * @param requestId 本次请求唯一 ID（由 {@link RpcClient#nextRequestId()} 生成）
     * @param future    业务线程将 await 的 Future 实例
     */
    public <T> void register(long requestId, RequestResponseFuture<T> future) {
        pending.put(requestId, future); // 写入映射，Netty IO 线程后续 remove 并完成
    }

    /**
     * 主动移除未完成的 Future（如连接断开、业务取消），避免内存泄漏。
     *
     * @param requestId 待移除的请求 ID
     */
    public void remove(long requestId) {
        pending.remove(requestId); // 从待完成表清除，不再接受该 ID 的回包
    }

    /**
     * 战斗服/中心服 RPC 响应到达时，匹配 requestId 并完成 Future。
     *
     * @param response 已反序列化的 {@link RpcForwardClientResponse} 等回包
     */
    public void complete(RpcForwardClientResponse response) {
        if (response == null) { // 空响应无法匹配 requestId
            return;
        }
        @SuppressWarnings("unchecked")
        RequestResponseFuture<RpcForwardClientResponse> future =
                (RequestResponseFuture<RpcForwardClientResponse>) pending.remove(response.getRequestId()); // 取回并移除，保证一次 RPC 只 complete 一次
        if (future != null) { // 存在对应的发起方仍在 await
            future.setResult(response); // 写入战斗服处理结果，唤醒游戏服业务线程
        }
    }

    /**
     * 按 RpcWireEnvelope.kind 分发入站 RPC 信封（由 Netty Handler 调用）。
     *
     * @param envelope  RpcLengthJsonDecoder 解码后的跨服信封
     * @param jsonCodec 用于将 body 转为具体 RPC 类型
     */
    public void dispatchEnvelope(RpcWireEnvelope envelope, RpcJsonCodec jsonCodec) {
        if (envelope == null || envelope.getKind() == null) { // 无法识别消息类型则忽略
            return;
        }
        if (RpcForwardClientResponse.class.getSimpleName().equals(envelope.getKind())) { // 客户端战斗转发回包
            RpcForwardClientResponse resp = jsonCodec.parseBody(envelope.getBody(), RpcForwardClientResponse.class); // JSON body → 强类型响应
            complete(resp); // 按 requestId 完成对应的 RequestResponseFuture
        }
    }
}
