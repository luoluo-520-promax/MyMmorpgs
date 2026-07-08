/**
 * 文件维护说明
 * 1) 文件路径：skill-service/src/main/java/cn/itcast/demo/mymmorpg/support/SkillServiceJmx.java
 * 2) 所属模块：skill-service / support
 * 3) 主要职责：JMX 暴露 skill_config 与 player_skill 表行数，监控技能配置与习得数据量。
 * 4) 变更建议：修改前先确认 JPA Repository 是否在 skill-service 进程可用。
 * 5) 风险提示：count() 在大表上可能较慢，生产环境慎用高频轮询。
 */
package cn.itcast.demo.mymmorpg.support; // skill-service 运维辅助类包

import cn.itcast.demo.mymmorpg.repository.PlayerSkillRepository; // player_skill 表 JPA 仓储，统计玩家习得记录行数
import cn.itcast.demo.mymmorpg.repository.SkillConfigRepository; // skill_config 表 JPA 仓储，统计静态技能模板行数
import org.springframework.jmx.export.annotation.ManagedAttribute; // 将 getter 暴露为 JMX 可读属性
import org.springframework.jmx.export.annotation.ManagedResource; // 注册本类为 JMX MBean
import org.springframework.stereotype.Component; // Spring 单例组件

/**
 * JMX 技能运维：DevDataLoader 写入后可通过 JConsole 确认配置是否生效。
 */
@Component // Spring 创建单例并配合 JMX 导出器注册 MBean
@ManagedResource( // MBean 元数据
        objectName = "cn.itcast.demo.mymmorpg:type=SkillService,name=Skill", // JConsole 路径：SkillService → Skill
        description = "技能系统" // MBean 描述
)
public class SkillServiceJmx { // JMX 门面：只读暴露 skill_config / player_skill 表行数

    private final SkillConfigRepository skillConfigRepository; // 查 skill_config 全表 count，验证 DevDataLoader 是否灌入模板
    private final PlayerSkillRepository playerSkillRepository; // 查 player_skill 全表 count，观察全服习得总量

    public SkillServiceJmx(SkillConfigRepository skillConfigRepository, PlayerSkillRepository playerSkillRepository) { // 构造器注入两个 JPA Repository
        this.skillConfigRepository = skillConfigRepository; // 保存 skill_config 仓储引用
        this.playerSkillRepository = playerSkillRepository; // 保存 player_skill 仓储引用
    }

    /** skill_config 静态模板总数 */
    @ManagedAttribute(description = "skill_config 行数") // JConsole 属性 skillConfigCount
    public long getSkillConfigCount() { // JMX getter
        return skillConfigRepository.count(); // SELECT COUNT(*) FROM skill_config，0 表示配置未加载或表空
    }

    /** player_skill 玩家习得记录总数 */
    @ManagedAttribute(description = "player_skill 行数") // JConsole 属性 playerSkillRowCount
    public long getPlayerSkillRowCount() { // JMX getter
        return playerSkillRepository.count(); // SELECT COUNT(*) FROM player_skill，反映全服玩家已学技能总量
    }
}
