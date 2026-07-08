package cn.itcast.demo.mymmorpg.config; // chat-service 策略 Bean 配置包，注册默认 ChatPolicy 匿名实现

import cn.itcast.demo.mymmorpg.support.ChatPolicy; // 敏感词过滤与频道解锁策略接口，ChatService.handleSendChat 发送前调用
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 容器内已有 ChatPolicy Bean（如 GroovyChatPolicy）时跳过本默认 Bean
import org.springframework.context.annotation.Bean; // 将 chatPolicy() 返回值注册为 Spring 单例 Bean
import org.springframework.context.annotation.Configuration; // 声明本类为 @Configuration 配置源

import java.util.List; // BLOCKED_CHAT_WORDS 不可变敏感词列表

/**
 * 提供 ChatPolicy 默认实现：空白拒绝、512 字截断、内置敏感词整句拦截。
 * GroovyChatPolicy 存在时本配置不生效。
 */
@Configuration(proxyBeanMethods = false) // 关闭 CGLIB 代理，@Bean 方法直接调用不经过代理，减少启动开销
public class ChatPolicyConfiguration { // 默认聊天内容审核策略的配置类

    /** 内置违禁词表：命中任一词则 filterContent 返回 null，ChatService 回 602 SENSITIVE */
    private static final List<String> BLOCKED_CHAT_WORDS = List.of("法轮", "赌博", "色情"); // 不可变列表，Groovy 版可扩展为读配置中心

    /**
     * 工厂方法：创建匿名 ChatPolicy，供 ChatService 在广播 603 前过滤消息正文。
     *
     * @return 默认敏感词+截断策略，isChannelUnlocked 沿用接口 default 全部频道开放
     */
    @Bean // 注册名为 chatPolicy 的 ChatPolicy Bean，构造注入 ChatService
    @ConditionalOnMissingBean(ChatPolicy.class) // GroovyChatPolicy @Component 已注册时避免双 Bean 冲突
    ChatPolicy chatPolicy() { // Spring 容器初始化时调用，生成默认内容过滤策略
        return new ChatPolicy() { // 匿名内部类，仅实现 filterContent，频道解锁用接口 default true
            @Override
            public String filterContent(int channel, int msgType, String rawContent) { // channel/msgType 预留按频道差异化审核，当前实现未区分
                if (rawContent == null || rawContent.isBlank()) { // null 或纯空白（空格/换行）不允许发送
                    return null; // 返回 null 表示拒绝，ChatService 回 ChatRetCode.SENSITIVE
                }
                String text = rawContent.trim(); // 去掉首尾空白，防止用空格绕过敏感词检测
                if (text.length() > 512) { // 单条消息超过 512 字符则截断，防止刷屏与 DB 字段溢出
                    text = text.substring(0, 512); // 保留前 512 个字符作为可发送正文
                }
                for (String word : BLOCKED_CHAT_WORDS) { // 逐条比对内置违禁词「法轮」「赌博」「色情」
                    if (text.contains(word)) { // 子串命中即整句拒绝，不做替换打码
                        return null; // ChatService 收到 null 后返回 602 retcode=SENSITIVE
                    }
                }
                return text; // 通过审核的 trim/截断后正文，写入 603 notify 与 chat_message_log
            }
        };
    }
}
