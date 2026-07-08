/**
 * 文件说明
 * 模块：mmorpg-common / 模型
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/model/MonsterWaveSimpleFactory.java
 * 类型：类
 * 职责：定义 MonsterWaveSimpleFactory，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.model;

// 导入项目类：MonsterConfig
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;

import org.springframework.stereotype.Component;


import java.util.ArrayList;

import java.util.List;

/**
 * 怪物波次简单工厂：
 * 在产品类型较少、创建逻辑简单时，统一由一个工厂类创建波次数据
 */
@Component
public class MonsterWaveSimpleFactory {

    /**
     * 按固定规则把怪物模板拆成波次（每波最多 3 个）。
     */
    /**
     * createwaves；参数：List<MonsterConfig> templates
     */
    public List<MonsterWave> createWaves(List<MonsterConfig> templates) {
        List<MonsterWave> waves = new ArrayList<>(); // 初始化可变列表
        if (templates == null || templates.isEmpty()) { // 条件分支判断
            return waves;
        }
        int waveNo = 1;  // 执行语句
        for (int i = 0; i < templates.size(); i += 3) { // 逐项处理集合元素
            int end = Math.min(i + 3, templates.size());
            /**
             * 构造 MonsterWave 实例
             */
            waves.add(new MonsterWave(waveNo++, new ArrayList<>(templates.subList(i, end))));
        }
        return waves;
    }

        public record MonsterWave(int waveNo, List<MonsterConfig> monsters) {
    }
}

