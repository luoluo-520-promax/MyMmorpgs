/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/IdempotencyService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：业务服务 IdempotencyService，承载核心领域逻辑。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 缓存上次成功回包，重传时直接 replay 跳过 Facade
import org.springframework.beans.factory.annotation.Value; // game.idempotency.* 开关、窗口、容量上限
import org.springframework.stereotype.Component; // MessageDispatchPipeline 注入，Netty/WebSocket 共用
import java.util.Iterator; // shrinkIfNeeded 遍历过期 Entry
import java.util.Map; // ConcurrentHashMap 的 entrySet 迭代删除
import java.util.concurrent.ConcurrentHashMap; // 多 stripe 并发读写幂等缓存
import java.util.zip.CRC32; // payload 指纹，与 session.marker+msgId 组成幂等键
/**
 * 消息幂等去重（协议无 requestId 版）：
 * <p>幂等键 = session.marker + msgId + CRC32(payload)；窗口内重复包 replay 缓存响应，跳过业务执行。</p>
 * <p>适用：客户端 TCP/WebSocket 重传、重复点击；窗口宜短（默认 5s），无法区分 payload 相同的不同请求。</p>
 */
@Component // Spring 单例，MessageDispatchPipeline dispatch 前 tryReplay、ClientRequestTask 成功后 record
public class IdempotencyService { // TCP/WebSocket 重传去重，避免 Facade 重复执行业务
    /** 缓存条目：写入时间与上次 ProtocolMessage 回包 */
    private record Entry(long tsMs, ProtocolMessage response) { // tsMs 用于窗口过期判定
    } // 编译单元结束

    /** game.idempotency.enabled，false 时 tryReplay/record 均短路 */
    private final boolean enabled; // game.idempotency.enabled 总开关
    /** 幂等窗口毫秒数，超时 Entry 视为失效 */
    private final long windowMs; // game.idempotency.window-ms 重复包去重窗口
    /** 缓存最大条目，超出时 sweep 过期键 */
    private final int maxEntries; // game.idempotency.max-entries 内存上限
    /** 幂等键 -> Entry，ConcurrentHashMap 支持多 dispatch stripe 并发访问 */
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>(); // marker:msgId:crc32 -> 上次 ScRsp
    public IdempotencyService( // 构造注入幂等开关/窗口/容量上限
            @Value("${game.idempotency.enabled:true}") boolean enabled, // false 时完全跳过幂等
            @Value("${game.idempotency.window-ms:5000}") long windowMs, // 默认 5s 窗口
            @Value("${game.idempotency.max-entries:20000}") int maxEntries) { // 默认最多 2 万条缓存
        this.enabled = enabled; // 记录幂等开关
        this.windowMs = Math.max(0, windowMs); // 负值钳制为 0 表示禁用窗口
        this.maxEntries = Math.max(100, maxEntries); // 至少 100 槽，避免配置过小频繁 sweep
    } // 编译单元结束

    /** dispatch 前调用：命中窗口内重复包则返回缓存 ProtocolMessage，调用方直接 send 跳过 Facade */
    public ProtocolMessage tryReplay(DispatchSession session, int msgId, byte[] payload) { // MessageDispatchPipeline stripe 内首步调用
        if (!enabled || windowMs <= 0) { // 功能关闭或窗口为 0
            return null; // 告知 pipeline 无 replay，继续 ClientRequestTask
        } // 编译单元结束

        String key = key(session, msgId, payload); // ws:sessionId:101:crc 或 netty:channelId:...
        Entry e = cache.get(key); // 查幂等缓存
        if (e == null) { // 首次到达或已过期清除
            return null; // 无缓存，正常执行业务
        } // 编译单元结束

        long now = System.currentTimeMillis(); // 当前毫秒时间戳
        if (now - e.tsMs > windowMs) { // 超出幂等窗口
            cache.remove(key, e); // 原子删除过期 Entry
            return null; // 视为新请求，重新执行业务
        } // 编译单元结束

        return e.response; // 重传包：直接 replay 上次 ProtocolMessage，session.send 出站
    } // 编译单元结束

    /** Facade 成功回包后写入缓存，供窗口内重传 replay */
    public void record(DispatchSession session, int msgId, byte[] payload, ProtocolMessage response) { // ClientRequestTask 成功 send 前调用
        if (!enabled || windowMs <= 0) { // 幂等未启用
            return; // 不写缓存
        } // 编译单元结束

        if (response == null) { // Facade 无回包
            return; // 无 ScRsp 可 replay
        } // 编译单元结束

        String key = key(session, msgId, payload); // marker:msgId:crc32 幂等键
        cache.put(key, new Entry(System.currentTimeMillis(), response)); // 缓存本次成功 ScRsp 与时间戳
        shrinkIfNeeded(); // 超 maxEntries 时 sweep 过期项
    } // 编译单元结束

    /** 条目数超过 maxEntries 时，移除窗口外过期键，单次最多删 1000 条避免长时间阻塞 */
    private void shrinkIfNeeded() { // record 后可能触发
        int size = cache.size(); // 当前缓存条目数
        if (size <= maxEntries) { // 未超上限
            return; // 无需 sweep
        } // 编译单元结束

        long now = System.currentTimeMillis(); // sweep 基准时间
        int removed = 0; // 本轮已删计数
        Iterator<Map.Entry<String, Entry>> it = cache.entrySet().iterator(); // 安全迭代删除
        while (it.hasNext() && (cache.size() > maxEntries || removed < 1000)) { // 超容量或本轮未满 1000 删
            Map.Entry<String, Entry> e = it.next(); // 取下一条 Entry
            if (now - e.getValue().tsMs > windowMs) { // 仅删窗口外过期项
                it.remove(); // 从 ConcurrentHashMap 移除
                removed++; // 计数 +1，单次最多 1000 避免阻塞 dispatch 线程过久
            } // 编译单元结束
        } // 编译单元结束
    } // 编译单元结束

    /** 组装幂等键：会话 marker 隔离不同连接，msgId 隔离不同协议，CRC32 隔离同 msgId 不同 payload */
    private static String key(DispatchSession session, int msgId, byte[] payload) { // tryReplay/record 共用
        String marker = session != null ? session.marker() : "unknown"; // netty:channelId 或 ws:sessionId
        long hash = crc32(payload); // payload 指纹
        return marker + ":" + msgId + ":" + hash; // 三段位幂等键
    } // 编译单元结束

    /** payload 为空时 hash=0，非空用 CRC32 快速指纹（非加密） */
    private static long crc32(byte[] payload) { // 幂等键第三段
        if (payload == null || payload.length == 0) { // 空 protobuf 体
            return 0; // 空 payload 统一 hash=0，同 msgId 空体视为同一请求
        } // 编译单元结束

        CRC32 crc = new CRC32(); // JDK CRC32 快速指纹
        crc.update(payload); // 累加 protobuf 字节
        return crc.getValue(); // 64 位 CRC 值作为 hash 段
    } // 编译单元结束
} // 编译单元结束
