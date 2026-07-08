/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/PasswordConfig.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：向容器注册 BCrypt 密码编码器，供账号注册、DevDataLoader 与 AdminUser 密码哈希使用。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.context.annotation.Bean; // 声明 PasswordEncoder 单例，AccountCredentialManager 构造器注入
import org.springframework.context.annotation.Configuration; // 密码相关 Bean 集中配置
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 默认 strength=10，适合游戏账号与 GM 后台
import org.springframework.security.crypto.password.PasswordEncoder; // 统一 encode/matches 接口，便于测试 Mock

@Configuration // 与 Web 安全解耦：player-service 仅用 crypto 模块，不启用完整 Spring Security Filter 链

public class PasswordConfig { // PasswordConfig 类型定义
    /**
     * 全局密码编码器：注册/改密时 encode，登录时 matches 校验明文与库中哈希。
     * BCrypt 每次 encode 盐值不同，同一明文产生不同密文，防彩虹表。
     */

    @Bean // Bean 名 passwordEncoder，DevDataLoader 与 AccountCredentialManager 按类型注入
    public PasswordEncoder passwordEncoder() { // 游戏账号与 GM 后台共用的 BCrypt 编码器
        return new BCryptPasswordEncoder(); // 使用默认 cost factor，生产可按 CPU 预算调高
    } // PasswordConfig 类体结束
} // 编译单元结束
