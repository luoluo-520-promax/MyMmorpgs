package cn.itcast.demo.mymmorpg.config; // skill-service 策略 Bean 配置

import cn.itcast.demo.mymmorpg.support.SkillPolicy; // 伤害/治疗数值策略接口，SkillService.handleCastSkill 调用 computeDamage/computeHeal
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 容器内无 SkillPolicy 实现时才注册本默认 Bean
import org.springframework.context.annotation.Bean; // 注册 SkillPolicy 匿名实现
import org.springframework.context.annotation.Configuration; // 声明配置类

/**
 * 提供 SkillPolicy 默认线性公式实现，可被 GroovySkillPolicy 覆盖
 */
@Configuration(proxyBeanMethods = false) // 关闭配置类 CGLIB 代理
public class SkillPolicyConfiguration {
    /**
     * 工厂方法：创建匿名 SkillPolicy，供 SkillService 施法结算调用
     * @return
     */
    @Bean // 将 skillPolicy() 返回值注册为 SkillPolicy Bean
    @ConditionalOnMissingBean(SkillPolicy.class) // GroovySkillPolicy 已注册时跳过，避免双 Bean 冲突
    SkillPolicy skillPolicy() {
        return new SkillPolicy() { // 匿名内部类实现 SkillPolicy 两个方法
            @Override
            public int computeDamage(int playerLevel, int skillId, int targetType) { // 敌对技能伤害公式：基础 80 + 等级×12 + skillId 余数扰动
                int lv = Math.max(1, playerLevel); // 等级下限 1，防止 0 级或 null 导致伤害为负
                return 80 + lv * 12 + skillId % 50; // 线性成长 + skillId%50 区分不同技能基础伤害
            }

            @Override
            public int computeHeal(int playerLevel, int skillId) { // 友方/自身治疗公式：基础 60 + 等级×8
                int lv = Math.max(1, playerLevel); // 等级下限 1
                return 60 + lv * 8; // 治疗量随等级线性增长，skillId 暂不参与（Groovy 版可扩展读 skill_config）
            }
        };
    }
}
