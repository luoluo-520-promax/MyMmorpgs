/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/java/cn/itcast/demo/mymmorpg/support/ChatServiceJmx.java
 * 2) 所属模块：chat-service / support
 * 3) 主要职责：JMX 暴露聊天审计条数、禁言、队伍/公会 Redis roster 运维操作。
 * 4) 变更建议：生产环境限制 JMX 端口访问，防止未授权 GM 操作。
 * 5) 风险提示：muteUntil/setPlayerTeam 直接写 Redis，误操作会影响在线玩家聊天。
 */
package cn.itcast.demo.mymmorpg.support; // JMX 运维 Bean 包，与 ChatPolicy 同包便于扫描

import cn.itcast.demo.mymmorpg.repository.ChatMessageLogRepository; // JPA 仓储，统计 chat_message_log 表总行数
import cn.itcast.demo.mymmorpg.service.ChatService; // 禁言与队伍/公会 roster 业务委托入口
import org.springframework.jmx.export.annotation.ManagedAttribute; // 将 getChatMessageLogCount 暴露为 JConsole 只读属性
import org.springframework.jmx.export.annotation.ManagedOperation; // 将 muteUntil/clearMute 等暴露为 JConsole 可调用操作
import org.springframework.jmx.export.annotation.ManagedOperationParameter; // 为 JConsole 操作参数提供中文说明
import org.springframework.jmx.export.annotation.ManagedResource; // 注册 JMX MBean，objectName 供 JConsole 连接定位
import org.springframework.stereotype.Component; // 注册为 Spring 单例，启动时自动注册到 JMX 服务器

/**
 * JMX 聊天运维：GM 通过 JConsole 查审计总量、禁言玩家、联调时设置队伍/公会频道成员。
 */
@Component // Spring 容器管理，与 ChatService 同生命周期
@ManagedResource( // 声明本类为 JMX 管理资源
        objectName = "cn.itcast.demo.mymmorpg:type=ChatService,name=Chat", // JConsole MBeans 树路径：ChatService → Chat
        description = "聊天系统" // MBean 中文描述，JConsole 属性面板展示
)
public class ChatServiceJmx { // 运维门面：不直接操作 Redis，委托 ChatService 保持业务逻辑单一

    private final ChatMessageLogRepository chatMessageLogRepository; // 异步落库的 chat_message_log 表 JPA 仓储
    private final ChatService chatService; // 聊天业务服务：setMuteUntil/clearMute/setPlayerTeam/setPlayerGuild

    /**
     * 构造器注入：审计仓储用于只读统计，聊天服务用于写 Redis 禁言与 roster。
     */
    public ChatServiceJmx(ChatMessageLogRepository chatMessageLogRepository, ChatService chatService) { // JMX Bean 由 Spring 自动装配
        this.chatMessageLogRepository = chatMessageLogRepository; // 持有仓储，getChatMessageLogCount 调用 count()
        this.chatService = chatService; // 持有业务服务，GM 操作委托给 ChatService 写 Redis
    }

    /**
     * JConsole 只读属性：chat_message_log 表当前总行数，用于监控聊天审计增速。
     */
    @ManagedAttribute(description = "chat_message_log 行数") // JConsole Attributes 页签展示，不可写
    public long getChatMessageLogCount() { // SELECT COUNT(*) FROM chat_message_log
        return chatMessageLogRepository.count(); // JPA count() 返回审计表累计行数，异常刷屏时可对比增速
    }

    /**
     * GM 禁言：将 chat:mute:until:{playerId} 设为指定截止 epoch 毫秒。
     * ChatService.isMuted 在截止前拒绝该玩家所有频道发送。
     */
    @ManagedOperation(description = "禁言至指定时间（毫秒时间戳，当前时间之后）") // JConsole Operations 页签可调用
    @ManagedOperationParameter(name = "playerId", description = "玩家 ID") // 第一个参数：被禁言的 player.id
    @ManagedOperationParameter(name = "epochMillis", description = "禁言截止时间") // 第二个参数：禁言截止 System.currentTimeMillis()
    public void muteUntil(long playerId, long epochMillis) { // GM 通过 JConsole 设置禁言截止时间
        chatService.setMuteUntil(playerId, epochMillis); // 委托 ChatService 写 Redis chat:mute:until:{playerId}
    }

    /**
     * GM 解除禁言：删除 Redis chat:mute:until:{playerId} 键。
     */
    @ManagedOperation(description = "解除禁言") // JConsole Operations 页签可调用
    public void clearMute(long playerId) { // 参数为被解除禁言的 player.id
        chatService.clearMute(playerId); // 委托 ChatService DEL chat:mute:until:{playerId}
    }

    /**
     * 联调：手动绑定玩家到队伍，维护 chat:player:team:{playerId} 与 chat:team:roster:{teamId}。
     * teamId 为空时退队，删除玩家→队伍映射。
     */
    @ManagedOperation(description = "设置玩家所在队伍（Redis），teamId 空则清除") // JConsole 联调队伍频道用
    public void setPlayerTeam(long playerId, String teamId) { // playerId=队员 ID，teamId=队伍业务 ID 字符串
        chatService.setPlayerTeam(playerId, teamId); // 委托 ChatService SET/SADD 或 DEL chat:player:team 键
    }

    /**
     * 联调：手动绑定玩家到公会，维护 chat:player:guild:{playerId} 与 chat:guild:roster:{guildId}。
     * guildId 为空时退公会，删除玩家→公会映射。
     */
    @ManagedOperation(description = "设置玩家所在公会（Redis），guildId 空则清除") // JConsole 联调公会频道用
    public void setPlayerGuild(long playerId, String guildId) { // playerId=成员 ID，guildId=公会业务 ID 字符串
        chatService.setPlayerGuild(playerId, guildId); // 委托 ChatService SET/SADD 或 DEL chat:player:guild 键
    }
}
