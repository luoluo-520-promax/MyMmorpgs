/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/java/cn/itcast/demo/mymmorpg/service/ChatService.java
 * 2) 所属模块：chat-service / service
 * 3) 主要职责：聊天 cmd=602/603，Redis 禁言/限频/队伍公会 roster，Groovy 过滤，MySQL 审计日志。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 聊天 602/603：Redis 禁言/限频/roster + Groovy 过滤 + chat_message_log 审计

import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.ChatRetCode;
import cn.itcast.demo.mymmorpg.protocol.MessageId; // SEND_CHAT_MSG_SC_RSP=602, CHAT_MSG_SC_NOTIFY=603
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一 msgId + payload
import cn.itcast.demo.mymmorpg.protocol.RetCode; // PLAYER_NOT_SELECTED / PLAYER_NOT_FOUND
import cn.itcast.demo.mymmorpg.protocol.protobuf.ChatMsgScNotify; // 603 推送：其他玩家收到的聊天通知
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq; // 602 发送聊天请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgScRsp; // 602 发送聊天响应
import cn.itcast.demo.mymmorpg.support.ChatPolicy; // Groovy 敏感词过滤与频道解锁判定
import org.springframework.data.redis.core.StringRedisTemplate; // chat:mute/rl/roster Redis 键操作
import org.springframework.stereotype.Service; // ChatFacade 协议 Handler 注入本服务

import java.time.Duration; // chat:rl:{playerId} 限频窗口 TTL 10 秒
import java.util.ArrayList; // 收集在线队伍/公会成员 playerId 推送目标
import java.util.HashSet; // 解析 chat:team/guild:roster SET 为 Long playerId
import java.util.List; // sendToPlayers 批量 603 推送
import java.util.Objects; // toString/requireNonNull 防 NPE
import java.util.Set; // Redis SMEMBERS 成员集合
import java.util.UUID; // 审计 cacheKey 唯一后缀防碰撞

/**
 * 聊天 602/603：Redis 禁言/限频/队伍公会 roster，Groovy 过滤，MySQL 审计日志。
 */
@Service // 聊天协议 Handler 与管理接口 setMute/setPlayerTeam 共用
public class ChatService { // handleSendChat 602 主入口，603 notify 按频道推送

    /** 私聊频道 protocol channel=1，需 target_id 指定接收者 */
    public static final int CHANNEL_PRIVATE = 1; // SendChatMsgCsReq.channel=1，1→1 私聊

    /** 世界频道 channel=2，广播全服在线玩家 */
    public static final int CHANNEL_WORLD = 2; // channel=2，broadcastAllOnline 603 推送

    /** 队伍频道 channel=3，仅队伍 roster 内在线成员 */
    public static final int CHANNEL_TEAM = 3; // channel=3，chat:team:roster:{teamId} 成员

    /** 公会频道 channel=4，仅公会 roster 内在线成员 */
    public static final int CHANNEL_GUILD = 4; // channel=4，chat:guild:roster:{guildId} 成员

    /** 文本消息 msg_type=1 */
    public static final int MSG_TEXT = 1; // msg_type=1 文本，Groovy filterContent 过滤

    /** 表情消息 msg_type=2 */
    public static final int MSG_EMOJI = 2; // msg_type=2 表情

    /** 系统消息 msg_type=3 */
    public static final int MSG_SYSTEM = 3; // msg_type=3 系统消息

    /** Redis 禁言键前缀 chat:mute:until:{playerId}，值为禁言截止 epochMillis */
    private static final String REDIS_MUTE = "chat:mute:until:"; // GET 判定 isMuted

    /** Redis 限频键前缀 chat:rl:{playerId}，INCR 计数，TTL 10 秒 */
    private static final String REDIS_RL = "chat:rl:"; // INCR + EXPIRE 10s 滑动窗口限频

    /** Redis 玩家→队伍 chat:player:team:{playerId}，值为 teamId 字符串 */
    private static final String REDIS_PLAYER_TEAM = "chat:player:team:"; // setPlayerTeam 维护

    /** Redis 队伍成员 roster chat:team:roster:{teamId}，SET 存 playerId 字符串 */
    private static final String REDIS_TEAM_ROSTER = "chat:team:roster:"; // SMEMBERS 队伍频道推送目标

    /** Redis 玩家→公会 chat:player:guild:{playerId}，值为 guildId 字符串 */
    private static final String REDIS_PLAYER_GUILD = "chat:player:guild:"; // setPlayerGuild 维护

    /** Redis 公会成员 roster chat:guild:roster:{guildId}，SET 存 playerId 字符串 */
    private static final String REDIS_GUILD_ROSTER = "chat:guild:roster:"; // SMEMBERS 公会频道推送目标

    /** 限频滑动窗口秒数，chat:rl:{playerId} TTL */
    private static final int RATE_WINDOW_SEC = 10; // EXPIRE chat:rl:{playerId} 10 秒

    /** 限频窗口内最大发送次数 */
    private static final int RATE_MAX = 5; // INCR >5 返回 ChatRetCode.RATE_LIMIT

    /** 客户端时间戳与服务端偏差上限 5 分钟，防重放 */
    private static final long TIMESTAMP_WINDOW_MS = 300_000L; // |serverTs-clientTs|>300000 拒绝

    private final PlayerCachePort playerCachePort; // 查发送者 Player 实体与私聊目标 existsById，独立部署时为 NoOp
    private final ChatAsyncDbService chatAsyncDbService; // chat:audit 缓存 + 异步写 chat_message_log
    private final StringRedisTemplate stringRedisTemplate; // chat:mute/rl/roster Redis 读写
    private final ChatPolicy chatPolicy; // Groovy 敏感词过滤与频道解锁
    private final ChatEventPublisher chatEventPublisher; // 发布 ChatSent MQ 领域事件
    private final PlayerNotificationPort playerNotificationPort; // WebSocket 推送 603、判断在线、broadcastAllOnline

    /**
     * 构造器注入：玩家缓存、审计写路径、Redis、内容策略、MQ 事件、WebSocket 推送端口。
     */
    public ChatService(
            PlayerCachePort playerCachePort, // 查 player 实体与存在性
            ChatAsyncDbService chatAsyncDbService, // 审计 cache-first + 异步落库
            StringRedisTemplate stringRedisTemplate, // Redis 禁言/限频/roster 键
            ChatPolicy chatPolicy, // 敏感词过滤与频道解锁
            ChatEventPublisher chatEventPublisher, // MQ ChatSent 事件
            PlayerNotificationPort playerNotificationPort) { // 603 推送与在线判断
        this.playerCachePort = playerCachePort; // 持有玩家缓存端口，handlePrivate 查 sender 与 target 存在性
        this.chatAsyncDbService = chatAsyncDbService; // 持有审计服务，persistLog 写 Redis + 异步 MySQL
        this.stringRedisTemplate = stringRedisTemplate; // 持有 Redis 模板，isMuted/tryRateLimit/roster 读写
        this.chatPolicy = chatPolicy; // 持有内容策略，filterContent/isChannelUnlocked 审核
        this.chatEventPublisher = chatEventPublisher; // 持有 MQ 发布器，各频道成功后 publishChatSent
        this.playerNotificationPort = playerNotificationPort; // 持有推送端口，603 notify 与 isOnline 判断
    }

    /** 发送聊天主入口：校验 → 过滤 → 按频道分发 */
    public ProtocolMessage handleSendChat(long senderPlayerId, SendChatMsgCsReq req) { // 发送聊天主入口：校验 → 过滤 → 按频道分发
        if (senderPlayerId <= 0) { // WebSocket 未绑定 playerId
            return rsp(RetCode.PLAYER_NOT_SELECTED, req.getChannel(), req.getTargetId(), "", "未选择角色"); // 602 PLAYER_NOT_SELECTED
        }

        int ch = req.getChannel(); // 频道 1私聊 2世界 3队伍 4公会
        int msgType = req.getMsgType(); // 1文本 2表情 3系统
        long targetId = req.getTargetId(); // 私聊目标 playerId
        long clientTs = req.getTimestamp(); // 客户端毫秒时间戳
        String raw = req.getContent() == null ? "" : req.getContent(); // 原始消息正文

        if (ch < CHANNEL_PRIVATE || ch > CHANNEL_GUILD) { // channel 不在 1~4
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, ch, targetId, "", "频道无效"); // 602 频道不可用
        }
        if (msgType < MSG_TEXT || msgType > MSG_SYSTEM) { // msgType 不在 1~3
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, ch, targetId, "", "消息类型无效"); // 602 消息类型非法
        }

        long serverTs = System.currentTimeMillis(); // 服务端权威时间戳
        if (Math.abs(serverTs - clientTs) > TIMESTAMP_WINDOW_MS) { // 时钟偏差超 5 分钟
            return rsp(ChatRetCode.RATE_LIMIT, ch, targetId, "", "时间戳异常，请重试"); // 602 防重放
        }

        if (isMuted(senderPlayerId)) { // chat:mute:until:{id} 未过期
            return rsp(ChatRetCode.MUTED, ch, targetId, "", "您已被禁言"); // 602 MUTED
        }

        if (!tryRateLimit(senderPlayerId)) { // chat:rl:{id} 10 秒内超 5 条
            return rsp(ChatRetCode.RATE_LIMIT, ch, targetId, "", "发送过于频繁，请稍后再试"); // 602 RATE_LIMIT
        }

        String filtered = chatPolicy.filterContent(ch, msgType, raw); // Groovy 敏感词替换/拦截
        if (filtered == null) { // 含违禁词被整句拒绝
            return rsp(ChatRetCode.SENSITIVE, ch, targetId, "", "消息包含敏感词，发送失败"); // 602 SENSITIVE
        }

        if (!chatPolicy.isChannelUnlocked(senderPlayerId, ch)) { // 等级/VIP 未达频道解锁
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, ch, targetId, "", "频道未解锁"); // 602 频道锁定
        }

        Player sender = playerCachePort.findById(senderPlayerId); // 查发送者 Player 缓存
        if (sender == null) { // playerId 无效
            return rsp(RetCode.PLAYER_NOT_FOUND, ch, targetId, "", "玩家不存在"); // 602 PLAYER_NOT_FOUND
        }
        String senderName = sender.getName() == null ? "" : sender.getName(); // 603 notify 展示发送者名

        return switch (ch) { // 按频道分发
            case CHANNEL_PRIVATE -> handlePrivate(senderPlayerId, senderName, targetId, msgType, filtered, serverTs); // 私聊 1→1
            case CHANNEL_WORLD -> handleWorld(senderPlayerId, senderName, msgType, filtered, serverTs); // 世界广播
            case CHANNEL_TEAM -> handleTeam(senderPlayerId, senderName, msgType, filtered, serverTs, targetId); // 队伍频道
            case CHANNEL_GUILD -> handleGuild(senderPlayerId, senderName, msgType, filtered, serverTs, targetId); // 公会频道
            default -> rsp(ChatRetCode.CHANNEL_UNAVAILABLE, ch, targetId, "", "频道不可用"); // switch 兜底：channel 不在 1~4 时不可用
        };
    }

    /** 私聊：校验目标在线 → 审计 → 603 推送给接收者 → 602 回发送者 */
    private ProtocolMessage handlePrivate( // 私聊：校验目标在线 → 审计 → 603 推送给接收者 → 602 回发送者
            long senderId, // 发送者 playerId
            String senderName, // 发送者角色名，603 notify 展示
            long targetId, // 私聊目标 playerId
            int msgType, // 1文本 2表情 3系统
            String filtered, // Groovy 过滤后正文
            long serverTs) { // 服务端权威毫秒时间戳
        if (targetId <= 0) { // 私聊必须指定 target_id
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_PRIVATE, targetId, "", "私聊需指定 target_id"); // 602 缺少目标
        }
        if (targetId == senderId) { // 不能私聊自己
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_PRIVATE, targetId, "", "不能私聊自己"); // 602 目标非法
        }
        if (!playerCachePort.existsById(targetId)) { // 目标 player 不存在
            return rsp(ChatRetCode.TARGET_OFFLINE, CHANNEL_PRIVATE, targetId, "", "目标玩家不存在"); // 602 TARGET_OFFLINE
        }
        if (!playerNotificationPort.isOnline(targetId)) { // 目标 WebSocket 不在线
            return rsp(ChatRetCode.TARGET_OFFLINE, CHANNEL_PRIVATE, targetId, "", "目标玩家离线"); // 602 私聊要求在线
        }

        persistLog(senderId, CHANNEL_PRIVATE, targetId, msgType, filtered, serverTs); // 写 chat:audit + 异步 chat_message_log
        chatEventPublisher.publishChatSent(senderId, CHANNEL_PRIVATE, targetId, msgType, filtered, serverTs); // MQ ChatSent

        byte[] notify = buildNotify(CHANNEL_PRIVATE, senderId, senderName, targetId, msgType, filtered, serverTs); // 603 payload
        playerNotificationPort.send(targetId, MessageId.CHAT_MSG_SC_NOTIFY, notify); // msgId=603 仅推接收者

        return rspOk(CHANNEL_PRIVATE, targetId, filtered); // msgId=602 成功回发送者
    }

    /** 世界频道：审计 → 广播 603 给全服在线（排除发送者避免重复） */
    private ProtocolMessage handleWorld( // 世界频道：审计 → 广播 603 给全服在线（排除发送者避免重复）
            long senderId, // 世界频道发送者 playerId
            String senderName, // 世界聊天展示名
            int msgType, // 消息类型 1~3
            String filtered, // 敏感词过滤后正文
            long serverTs) { // 服务端时间戳写入 603 notify
        persistLog(senderId, CHANNEL_WORLD, null, msgType, filtered, serverTs); // 世界聊天 targetId=null
        chatEventPublisher.publishChatSent(senderId, CHANNEL_WORLD, 0L, msgType, filtered, serverTs); // MQ 世界 ChatSent

        byte[] notify = buildNotify(CHANNEL_WORLD, senderId, senderName, 0L, msgType, filtered, serverTs); // 603 世界消息体
        playerNotificationPort.broadcastAllOnline(MessageId.CHAT_MSG_SC_NOTIFY, notify, senderId); // 全服在线除发送者

        return rspOk(CHANNEL_WORLD, 0L, filtered); // 602 成功，发送者靠 602 展示
    }

    /** 队伍频道：查 Redis roster → 推送给在线队员 */
    private ProtocolMessage handleTeam( // 队伍频道：查 Redis roster → 推送给在线队员
            long senderId, // 队伍频道发送者 playerId
            String senderName, // 队员可见的发送者名
            int msgType, // 文本/表情/系统
            String filtered, // 过滤后消息正文
            long serverTs, // 服务端毫秒时间戳
            long requestTargetEcho) { // 602 回显 targetId，队伍频道通常为 0
        String teamId = stringRedisTemplate.opsForValue().get(REDIS_PLAYER_TEAM + senderId); // GET chat:player:team:{senderId}
        if (teamId == null || teamId.isBlank()) { // 未加入队伍
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_TEAM, requestTargetEcho, "", "未加入队伍，队伍频道不可用"); // 602 无队伍
        }
        Set<String> raw = stringRedisTemplate.opsForSet().members(REDIS_TEAM_ROSTER + teamId); // SMEMBERS chat:team:roster:{teamId}
        if (raw == null || raw.isEmpty()) { // roster 空
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_TEAM, requestTargetEcho, "", "队伍频道未解锁"); // 602 roster 无效
        }
        Set<Long> members = new HashSet<>(); // 存放 chat:team:roster:{teamId} 解析后的 Long 型 playerId
        for (String s : raw) { // 遍历 Redis SMEMBERS 返回的 playerId 字符串集合
            try { // 将 roster 成员字符串转为 Long，供 members.contains(senderId) 校验
                members.add(Long.parseLong(s.trim())); // "12345" → 12345L，trim 去除 Redis 存储时的首尾空格
            } catch (NumberFormatException ignored) { // roster 中非数字脏数据（如空串）跳过，不影响其他成员推送
            }
        }
        if (!members.contains(senderId)) { // 发送者不在 roster
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_TEAM, requestTargetEcho, "", "不在该队伍中"); // 602 非队员
        }

        persistLog(senderId, CHANNEL_TEAM, null, msgType, filtered, serverTs); // 队伍聊天审计
        chatEventPublisher.publishChatSent(senderId, CHANNEL_TEAM, 0L, msgType, filtered, serverTs); // MQ 队伍 ChatSent

        byte[] notify = buildNotify(CHANNEL_TEAM, senderId, senderName, 0L, msgType, filtered, serverTs); // 603 队伍消息体
        List<Long> online = new ArrayList<>(); // 收集当前 WebSocket 在线的队员 playerId，供 sendToPlayers 批量推送
        for (Long pid : members) { // 遍历 chat:team:roster:{teamId} 全部成员（含发送者本人）
            if (playerNotificationPort.isOnline(pid)) { // 查该队员 WebSocket 会话是否仍连接 gateway
                online.add(pid); // 在线队员加入 603 推送目标列表
            }
        }
        if (online.isEmpty()) { // 全员离线
            return rsp(ChatRetCode.TARGET_OFFLINE, CHANNEL_TEAM, requestTargetEcho, "", "队伍成员均不在线"); // 602 无在线队员
        }
        playerNotificationPort.sendToPlayers(online, MessageId.CHAT_MSG_SC_NOTIFY, notify); // 批量 603 推在线队员

        return rspOk(CHANNEL_TEAM, 0L, filtered); // 602 成功
    }

    /** 公会频道：逻辑同队伍，键为 chat:player:guild / chat:guild:roster */
    private ProtocolMessage handleGuild( // 公会频道：逻辑同队伍，键为 chat:player:guild / chat:guild:roster
            long senderId, // 公会频道发送者 playerId
            String senderName, // 公会成员可见发送者名
            int msgType, // 消息类型
            String filtered, // 敏感词过滤后正文
            long serverTs, // 服务端时间戳
            long targetEcho) { // 602 回显 targetId
        String guildId = stringRedisTemplate.opsForValue().get(REDIS_PLAYER_GUILD + senderId); // GET chat:player:guild:{senderId}
        if (guildId == null || guildId.isBlank()) { // 未加入公会
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_GUILD, targetEcho, "", "未加入公会，公会频道不可用"); // 602 无公会
        }
        Set<String> raw = stringRedisTemplate.opsForSet().members(REDIS_GUILD_ROSTER + guildId); // SMEMBERS chat:guild:roster:{guildId}
        if (raw == null || raw.isEmpty()) { // roster 空
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_GUILD, targetEcho, "", "公会频道未解锁"); // 602 roster 无效
        }
        Set<Long> members = new HashSet<>(); // 存放 chat:guild:roster:{guildId} 解析后的 Long 型 playerId
        for (String s : raw) { // 遍历 Redis SMEMBERS 返回的公会成员 playerId 字符串
            try { // 将 roster 成员字符串转为 Long，供 members.contains(senderId) 校验
                members.add(Long.parseLong(s.trim())); // "67890" → 67890L，trim 去除存储时的首尾空格
            } catch (NumberFormatException ignored) { // roster 中非数字脏数据跳过，不影响其他成员推送
            }
        }
        if (!members.contains(senderId)) { // 发送者不在公会 roster
            return rsp(ChatRetCode.CHANNEL_UNAVAILABLE, CHANNEL_GUILD, targetEcho, "", "不在该公会中"); // 602 非成员
        }

        persistLog(senderId, CHANNEL_GUILD, null, msgType, filtered, serverTs); // 公会聊天审计
        chatEventPublisher.publishChatSent(senderId, CHANNEL_GUILD, 0L, msgType, filtered, serverTs); // MQ 公会 ChatSent

        byte[] notify = buildNotify(CHANNEL_GUILD, senderId, senderName, 0L, msgType, filtered, serverTs); // 603 公会消息体
        List<Long> online = new ArrayList<>(); // 收集当前 WebSocket 在线的公会成员 playerId
        for (Long pid : members) { // 遍历 chat:guild:roster:{guildId} 全部成员（含发送者本人）
            if (playerNotificationPort.isOnline(pid)) { // 查该成员 WebSocket 会话是否仍连接 gateway
                online.add(pid); // 在线成员加入 603 推送目标列表
            }
        }
        if (online.isEmpty()) { // 全员离线
            return rsp(ChatRetCode.TARGET_OFFLINE, CHANNEL_GUILD, targetEcho, "", "公会成员均不在线"); // 602 无在线成员
        }
        playerNotificationPort.sendToPlayers(online, MessageId.CHAT_MSG_SC_NOTIFY, notify); // 批量 603 推在线成员

        return rspOk(CHANNEL_GUILD, 0L, filtered); // 602 成功
    }

    /** Cache-First 审计：先写 Redis entity:chatAudit，再 @Async 落 chat_message_log */
    private void persistLog(long senderId, int channel, Long targetId, int msgType, String content, long serverTs) { // Cache-First 审计：先写 Redis entity:chatAudit，再 @Async 落 chat_message_log
        String cacheKey = "chat:audit:" + senderId + ":" + channel + ":" + serverTs + ":" + UUID.randomUUID(); // 唯一审计键防重复
        var entry = new ChatAsyncDbService.ChatAuditEntry( // 构造审计条目 DTO 供缓存与异步落库
                cacheKey, // Redis 审计 area 键
                senderId, // 发送者 playerId
                channel, // 频道 1~4
                targetId, // 私聊 targetId，世界/队/公会为 null
                msgType, // 消息类型 1~3
                content, // 过滤后正文
                serverTs); // 服务端时间戳
        chatAsyncDbService.cacheFirst(ChatAsyncDbService.CHAT_AUDIT_CACHE_AREA, entry); // 同步写 Redis chat:audit 区
        chatAsyncDbService.persistAsync(entry); // @Async INSERT chat_message_log
    }

    /** 查 chat:mute:until:{playerId}，值=禁言截止 epochMillis */
    private boolean isMuted(long playerId) { // 查 chat:mute:until:{playerId}，值=禁言截止 epochMillis
        String v = stringRedisTemplate.opsForValue().get(REDIS_MUTE + playerId); // GET chat:mute:until:{playerId}
        if (v == null) { // 无禁言记录
            return false; // 未禁言
        }
        try { // 解析 Redis chat:mute:until:{playerId} 值为 Long 型截止毫秒时间戳
            long until = Long.parseLong(v); // 禁言截止 epochMillis，由 setMuteUntil JMX 写入
            return System.currentTimeMillis() < until; // 当前时间未过截止点则仍禁言，handleSendChat 回 MUTED
        } catch (NumberFormatException e) { // Redis 值被误写为非数字时降级为未禁言，避免阻断全体聊天
            return false; // 解析失败视为无禁言记录，允许发送
        }
    }

    /** 滑动窗口限频：chat:rl:{playerId} INCR，首次 SETEX 10 秒，超 5 次拒绝 */
    private boolean tryRateLimit(long playerId) { // 滑动窗口限频：chat:rl:{playerId} INCR，首次 SETEX 10 秒，超 5 次拒绝
        String k = REDIS_RL + playerId; // chat:rl:{playerId}
        Long c = stringRedisTemplate.opsForValue().increment(k); // INCR 窗口内发送计数
        if (c != null && c == 1L) { // 窗口内首条消息
            stringRedisTemplate.expire(k, Objects.requireNonNull(Duration.ofSeconds(RATE_WINDOW_SEC))); // EXPIRE 10 秒滑动窗口
        }
        return c == null || c <= RATE_MAX; // 计数 ≤5 允许，>5 拒绝
    }

    /** 构造 603 ChatMsgScNotify Protobuf 字节 */
    private static byte[] buildNotify( // 构造 603 ChatMsgScNotify Protobuf 字节
            int channel, // 603 notify 频道 1~4
            long senderId, // 发送者 playerId
            String senderName, // 发送者角色名
            long targetId, // 私聊目标，世界/队/公会为 0
            int msgType, // 1文本 2表情 3系统
            String content, // 过滤后消息正文
            long serverTs) { // 服务端毫秒时间戳
        var b = ChatMsgScNotify.newBuilder() // 603 notify 构建器
                .setChannel(channel) // 频道 1~4
                .setSenderId(senderId) // 发送者 playerId
                .setSenderName(Objects.toString(senderName, "")) // 发送者名
                .setMsgType(msgType) // 消息类型
                .setContent(content) // 过滤后正文
                .setTimestamp(serverTs); // 服务端时间戳
        if (targetId > 0) { // 私聊频道
            b.setTargetId(targetId); // 私聊目标 playerId
        }
        return b.build().toByteArray(); // 603 payload 字节
    }

    /** 602 成功响应 */
    private static ProtocolMessage rspOk(int channel, long targetId, String filtered) { // 602 成功响应
        var r = SendChatMsgScRsp.newBuilder() // 602 成功响应
                .setRetcode(ChatRetCode.OK) // retcode=OK
                .setChannel(channel) // 频道
                .setFilteredContent(filtered); // 过滤后内容供客户端展示
        if (targetId > 0) { // 私聊
            r.setTargetId(targetId); // 回显 targetId
        }
        return new ProtocolMessage(MessageId.SEND_CHAT_MSG_SC_RSP, r.build().toByteArray()); // msgId=602
    }

    /** 602 失败响应，含 errorMsg 中文提示 */
    private static ProtocolMessage rsp(int code, int channel, long targetId, String filtered, String errorMsg) { // 602 失败响应，含 errorMsg 中文提示
        var r = SendChatMsgScRsp.newBuilder() // 602 失败响应
                .setRetcode(code) // ChatRetCode 或 RetCode
                .setChannel(channel); // 频道
        if (targetId > 0) { // 私聊回显
            r.setTargetId(targetId); // targetId
        }
        if (filtered != null && !filtered.isEmpty()) { // 部分失败仍有过滤内容
            r.setFilteredContent(filtered); // 过滤后正文
        }
        if (errorMsg != null && !errorMsg.isEmpty()) { // 中文错误提示
            r.setErrorMsg(errorMsg); // 客户端 toast
        }
        return new ProtocolMessage(MessageId.SEND_CHAT_MSG_SC_RSP, r.build().toByteArray()); // msgId=602
    }

    /** JMX/管理：设置禁言截止时间 epochMillis */
    public void setMuteUntil(long playerId, long epochMillis) { // JMX/管理：设置禁言截止时间 epochMillis
        stringRedisTemplate.opsForValue().set(REDIS_MUTE + playerId, Objects.requireNonNull(String.valueOf(epochMillis))); // SET chat:mute:until:{playerId}
    }

    /** JMX/管理：清除禁言 */
    public void clearMute(long playerId) { // JMX/管理：清除禁言
        stringRedisTemplate.delete(REDIS_MUTE + playerId); // DEL chat:mute:until:{playerId}
    }

    /** 管理/队伍系统：绑定玩家到队伍，维护 roster SET */
    public void setPlayerTeam(long playerId, String teamId) { // 管理/队伍系统：绑定玩家到队伍，维护 roster SET
        if (teamId == null || teamId.isBlank()) { // 退队
            stringRedisTemplate.delete(REDIS_PLAYER_TEAM + playerId); // DEL chat:player:team:{playerId}
            return; // 不再维护 roster
        }
        stringRedisTemplate.opsForValue().set(REDIS_PLAYER_TEAM + playerId, Objects.requireNonNull(teamId)); // SET chat:player:team:{playerId}
        stringRedisTemplate.opsForSet().add(REDIS_TEAM_ROSTER + teamId, String.valueOf(playerId)); // SADD chat:team:roster:{teamId}
    }

    /** 管理/公会系统：绑定玩家到公会，维护 roster SET */
    public void setPlayerGuild(long playerId, String guildId) { // 管理/公会系统：绑定玩家到公会，维护 roster SET
        String oldGuild = stringRedisTemplate.opsForValue().get(REDIS_PLAYER_GUILD + playerId);
        if (oldGuild != null && !oldGuild.isBlank()) {
            stringRedisTemplate.opsForSet().remove(REDIS_GUILD_ROSTER + oldGuild, String.valueOf(playerId));
        }
        if (guildId == null || guildId.isBlank()) { // 退公会
            stringRedisTemplate.delete(REDIS_PLAYER_GUILD + playerId); // DEL chat:player:guild:{playerId}
            return; // 不再维护 roster
        }
        stringRedisTemplate.opsForValue().set(REDIS_PLAYER_GUILD + playerId, Objects.requireNonNull(guildId)); // SET chat:player:guild:{playerId} = guildId
        stringRedisTemplate.opsForSet().add(REDIS_GUILD_ROSTER + guildId, String.valueOf(playerId)); // SADD chat:guild:roster:{guildId}
    }

    /** 公会创建后初始化聊天侧 channel 元数据键（roster 仍由成员加入维护）。 */
    public void onGuildCreated(String guildId) {
        if (guildId == null || guildId.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForValue().set("chat:guild:" + guildId, "1");
    }
}
