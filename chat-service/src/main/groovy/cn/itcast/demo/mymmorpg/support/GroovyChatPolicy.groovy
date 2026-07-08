/**
 * 文件维护说明
 * 1) 文件路径：chat-service/src/main/groovy/cn/itcast/demo/mymmorpg/support/GroovyChatPolicy.groovy
 * 2) 所属模块：chat-service / groovy
 * 3) 主要职责：Groovy 实现的 ChatPolicy，覆盖 ChatPolicyConfiguration 默认敏感词过滤。
 * 4) 变更建议：可在此注入 ChatConfigRepository 按 channel 读取专属违禁词表。
 * 5) 风险提示：公式变更影响 602 filteredContent 与 chat_message_log 审计内容。
 */
package cn.itcast.demo.mymmorpg.support // 与 Java ChatPolicy 同包，Spring 扫描注册为 @Component

import groovy.transform.CompileStatic // 静态编译 filterContent/isChannelUnlocked，热路径无 Groovy 动态派发开销
import org.springframework.stereotype.Component // 注册 Bean，存在则 @ConditionalOnMissingBean 默认 ChatPolicy 不生效

@Component // Spring 单例；优先级高于 ChatPolicyConfiguration 匿名 ChatPolicy
@CompileStatic // 方法体编译为与 Java 等价的字节码
class GroovyChatPolicy implements ChatPolicy { // Groovy 版内容审核，当前逻辑与 Java 默认实现一致

    /** 内置违禁词：命中任一词 filterContent 返回 null，ChatService 回 602 SENSITIVE */
    private static final List<String> BLOCKED = List.of('法轮', '赌博', '色情') // 不可变列表，可扩展为读 Nacos 配置

    @Override // 实现 ChatPolicy.filterContent
    String filterContent(int channel, int msgType, String rawContent) { // channel/msgType 预留差异化审核，当前未使用
        if (rawContent == null || rawContent.isBlank()) { // null 或纯空白不允许发送
            return null // ChatService 判定为敏感/非法内容，返回 ChatRetCode.SENSITIVE
        }
        String t = rawContent.trim() // 去掉首尾空白，统一审核基准
        if (t.length() > 512) { // 超过 512 字符截断，防止刷屏与 DB content 字段溢出
            t = t.substring(0, 512) // 保留前 512 字符
        }
        for (String w : BLOCKED) { // 逐条检测「法轮」「赌博」「色情」子串
            if (t.contains(w)) { // 命中违禁词整句拒绝，不做替换打码
                return null // ChatService 不广播 603，发送者收到 SENSITIVE
            }
        }
        t // Groovy 末行表达式即返回值：通过审核的 trim/截断后正文
    }

    @Override // 实现 ChatPolicy.isChannelUnlocked，覆盖接口 default
    boolean isChannelUnlocked(long senderPlayerId, int channel) { // senderPlayerId/channel 预留等级/VIP 校验
        true // 当前全部频道开放；队伍/公会实际可用性由 ChatService Redis roster 二次校验
    }
}
