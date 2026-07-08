/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/java/cn/itcast/demo/mymmorpg/service/ChatAsyncDbService.java
 * 2) 所属模块：chat-service / service
 * 3) 主要职责：聊天审计写路径分离，先写 Redis entity:chatAudit 再异步 INSERT chat_message_log。
 * 4) 变更建议：修改前先确认 dynamicCacheResolver 与 entity:chatAudit 缓存区配置一致。
 * 5) 风险提示：@Async 落库失败不阻塞 602 响应，需监控异步线程池异常日志。
 */
package cn.itcast.demo.mymmorpg.service; // 聊天审计 cache-first 写路径，主聊天 602/603 响应不等待 MySQL

import cn.itcast.demo.mymmorpg.entity.ChatMessageLog; // chat_message_log 表 JPA 实体，字段 sender_id/channel/content 等
import cn.itcast.demo.mymmorpg.repository.ChatMessageLogRepository; // 异步 persistAsync 调用 save() INSERT 审计行
import org.springframework.cache.annotation.CachePut; // cacheFirst 方法执行后将 entry 写入 Redis entity:chatAudit 区
import org.springframework.scheduling.annotation.Async; // persistAsync 在 chatAsyncExecutor 线程池执行，不阻塞主线程
import org.springframework.stereotype.Service; // ChatService.persistLog 在 602 成功路径调用本类

/**
 * 非核心写路径：先同步写 Redis entity:chatAudit 供 GM 即时查询，再 @Async 落 MySQL chat_message_log。
 */
@Service // Spring 单例，ChatService 构造器注入
public class ChatAsyncDbService { // 审计写路径与 602 SendChatMsgScRsp 主路径解耦，降低发送延迟

    /** dynamicCacheResolver 解析的缓存区域名，对应 Redis key 前缀 entity:chatAudit:: */
    public static final String CHAT_AUDIT_CACHE_AREA = "entity:chatAudit"; // cacheFirst 第一个参数，选择 entity:chatAudit 缓存区

    private final ChatMessageLogRepository chatMessageLogRepository; // JPA 仓储，@Async persistAsync 写入 chat_message_log 表

    /**
     * 构造器注入 chat_message_log 仓储。
     */
    public ChatAsyncDbService(ChatMessageLogRepository chatMessageLogRepository) { // Spring 自动装配 JPA Repository
        this.chatMessageLogRepository = chatMessageLogRepository; // 持有仓储供 persistAsync INSERT 审计行
    }

    /**
     * Cache-First 同步写 Redis：将审计条目放入 entity:chatAudit 区，key=entry.cacheKey。
     * GM 审计查询可立即命中缓存，无需等待 MySQL 落库。
     *
     * @param cacheArea 缓存区名，固定传 CHAT_AUDIT_CACHE_AREA="entity:chatAudit"
     * @param entry     审计 DTO，含 senderId/channel/content/serverTs 等
     * @return 原样返回 entry，@CachePut 以返回值作为缓存 value
     */
    @CachePut(cacheResolver = "dynamicCacheResolver", key = "#entry.cacheKey") // SET entity:chatAudit::{cacheKey} = entry
    public ChatAuditEntry cacheFirst(String cacheArea, ChatAuditEntry entry) { // ChatService.persistLog 同步调用，602 响应前完成
        return entry; // 返回值即 Redis 缓存 value，供 GM JMX/管理后台按 cacheKey 查询最近聊天
    }

    /**
     * 异步持久化：将审计 DTO 映射为 ChatMessageLog 实体并 INSERT chat_message_log。
     * 失败不影响已返回的 602 成功响应，需靠日志与监控告警。
     *
     * @param entry ChatService.persistLog 构造的审计条目
     */
    @Async // Spring @EnableAsync 后在独立线程池执行，主线程已返回 602 SendChatMsgScRsp
    public void persistAsync(ChatAuditEntry entry) { // 聊天发送成功后异步落库，不增加玩家感知延迟
        ChatMessageLog row = new ChatMessageLog(); // 新建 JPA 实体，对应 chat_message_log 表一行
        row.setSenderId(entry.senderId); // sender_id = 发送者 player.id，与 603 notify.sender_id 一致
        row.setChannel(entry.channel); // channel = 1私聊/2世界/3队伍/4公会
        row.setTargetId(entry.targetId); // target_id = 私聊接收者 player.id；世界/队伍/公会频道为 null
        row.setMsgType(entry.msgType); // msg_type = 1文本/2表情/3系统
        row.setContent(entry.content); // content = Groovy 过滤后的消息正文，最长 512 字符
        row.setServerTs(entry.serverTs); // server_ts = 服务端 epochMillis，与 603 notify.timestamp 一致
        chatMessageLogRepository.save(row); // JPA save → INSERT INTO chat_message_log(...)
    }

    /**
     * 聊天审计缓存与异步落库共用的不可变数据载体（Java record）。
     *
     * @param cacheKey  Redis 唯一键 chat:audit:{senderId}:{channel}:{serverTs}:{uuid}
     * @param senderId  发送者 player.id
     * @param channel   频道 1~4
     * @param targetId  私聊目标 player.id，非私聊为 null
     * @param msgType   消息类型 1~3
     * @param content   过滤后正文
     * @param serverTs  服务端毫秒时间戳
     */
    public record ChatAuditEntry( // record 自动生成构造器/getter/equals/hashCode
            String cacheKey, // Redis entity:chatAudit 区键，UUID 后缀防同一毫秒重复
            long senderId, // 发送者 player.id
            int channel, // 频道号，对应 ChatService.CHANNEL_PRIVATE~CHANNEL_GUILD
            Long targetId, // 私聊目标 player.id，世界/队伍/公会为 null
            int msgType, // 消息类型，对应 ChatService.MSG_TEXT/MSG_EMOJI/MSG_SYSTEM
            String content, // Groovy 过滤后正文，写入 603 与 DB
            long serverTs) { // 服务端 epochMillis，ChatService.handleSendChat 的 serverTs 参数
    }
}
