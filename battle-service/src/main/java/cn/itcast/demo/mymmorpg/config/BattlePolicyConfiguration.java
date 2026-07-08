/**
 * 文件说明：战斗与 Buff 策略 Bean 的默认配置类。
 * 职责：当容器中不存在自定义 BattlePolicy / BuffPolicy 实现时，注册内置默认策略 Bean。
 * 注意：默认伤害计算支持技能 1.5 倍加成，治疗按道具 ID 区分数值。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.BattlePolicy; // 战斗数值策略接口
import cn.itcast.demo.mymmorpg.support.BuffPolicy; // Buff 规则策略接口
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 仅当容器中无对应 Bean 时注册
import org.springframework.context.annotation.Bean; // 声明 Spring Bean
import org.springframework.context.annotation.Configuration; // 标记为配置类

/**
 * 战斗与 Buff 策略的默认 Bean 配置：提供可替换的默认实现。
 */
@Configuration(proxyBeanMethods = false) // 配置类，禁用代理以提升性能
public class BattlePolicyConfiguration { // 策略 Bean 注册配置

    /**
     * 注册默认战斗数值策略：伤害 = max(1, 攻击 - 防御)，技能额外 1.5 倍。
     *
     * @return 默认 BattlePolicy 匿名实现
     */
    @Bean // 注册 BattlePolicy Bean
    @ConditionalOnMissingBean(BattlePolicy.class) // 无自定义实现时使用默认策略
    BattlePolicy battlePolicy() { // 创建默认战斗策略
        return new BattlePolicy() { // 匿名实现 BattlePolicy 接口
            @Override
            public int computeDamage(int attackerAttack, int targetDefense, int actionType, int skillId) { // 计算伤害
                int base = Math.max(1, attackerAttack - targetDefense); // 基础伤害 = 攻击减防御，至少为 1
                if (actionType == 2 && skillId > 0) { // 技能行动且指定了技能 ID
                    return (int) (base * 1.5d); // 技能伤害 1.5 倍
                }
                return base; // 普通攻击返回基础伤害
            }

            @Override
            public int computeHeal(int actionType, int itemId) { // 计算治疗量
                if (actionType != 3) { // 非道具行动
                    return 0; // 不治疗
                }
                return itemId > 0 ? 150 : 100; // 有道具 ID 治疗 150，否则 100
            }
        };
    }

    /**
     * 注册默认 Buff 规则策略：使用接口中的默认方法（全部允许）。
     *
     * @return 默认 BuffPolicy 匿名实现
     */
    @Bean // 注册 BuffPolicy Bean
    @ConditionalOnMissingBean(BuffPolicy.class) // 无自定义实现时使用默认策略
    BuffPolicy buffPolicy() { // 创建默认 Buff 策略
        return new BuffPolicy() { // 匿名实现，沿用接口默认方法
        };
    }
}
