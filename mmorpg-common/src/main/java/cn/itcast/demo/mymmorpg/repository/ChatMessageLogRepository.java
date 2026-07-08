/**
 * 文件说明
 * 模块：mmorpg-common / 数据仓储
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/repository/ChatMessageLogRepository.java
 * 类型：接口
 * 职责：聊天消息日志表（chat_message_log）的 JPA 仓储，持久化聊天记录供审计与追溯。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 聊天日志仓储

import cn.itcast.demo.mymmorpg.entity.ChatMessageLog; // 单条聊天记录：发送者、频道、内容、时间戳等

import org.springframework.data.jpa.repository.JpaRepository; // 提供 save 写入日志

/**
 * 聊天消息日志数据访问接口。
 * <p>聊天服务发送成功后调用 {@link #save(Object)} 异步或同步落库；查询历史可扩展分页方法。</p>
 */
public interface ChatMessageLogRepository extends JpaRepository<ChatMessageLog, Long> { // 主键 logId 为 Long
    // 当前仅写入日志，未定义按频道/玩家分页查询
}
