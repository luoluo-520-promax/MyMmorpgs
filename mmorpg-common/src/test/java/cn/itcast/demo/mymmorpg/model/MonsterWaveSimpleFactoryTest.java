/**
 * 文件说明
 * 模块：mmorpg-common / 模型
 * 路径：src/test/java/cn/itcast/demo/mymmorpg/model/MonsterWaveSimpleFactoryTest.java
 * 类型：类
 * 职责：定义 MonsterWaveSimpleFactoryTest，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
// 声明包名
package cn.itcast.demo.mymmorpg.model;

// 怪物配置实体
// 导入项目类：MonsterConfig
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;

import org.testng.annotations.Test;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// MonsterWaveSimpleFactory 单元测试
public class MonsterWaveSimpleFactoryTest {

    // 被测工厂实例
    /**
     * 构造 MonsterWaveSimpleFactory 实例
     */
    private final MonsterWaveSimpleFactory factory = new MonsterWaveSimpleFactory();

    // null 或空列表应返回空波次
    @Test
    /**
     * createwaves_nullorempty_returnsempty；无参数
     */
    public void createWaves_nullOrEmpty_returnsEmpty() {
        assertThat(factory.createWaves(null)).isEmpty();
        assertThat(factory.createWaves(List.of())).isEmpty();
    } // createWaves_nullOrEmpty_returnsEmpty 结束

    // 4 只怪应分成 2 波：第一波 3 只，第二波 1 只
    @Test
    /**
     * createwaves_groupsbythree；无参数
     */
    public void createWaves_groupsByThree() {
        MonsterConfig m1 = cfg(1);
        MonsterConfig m2 = cfg(2);
        MonsterConfig m3 = cfg(3);
        MonsterConfig m4 = cfg(4);
        List<MonsterWaveSimpleFactory.MonsterWave> waves = factory.createWaves(List.of(m1, m2, m3, m4));
        assertThat(waves).hasSize(2);
        assertThat(waves.get(0).waveNo()).isEqualTo(1);  // 读取映射或对象属性
        assertThat(waves.get(0).monsters()).containsExactly(m1, m2, m3);  // 读取映射或对象属性
        assertThat(waves.get(1).waveNo()).isEqualTo(2);  // 读取映射或对象属性
        assertThat(waves.get(1).monsters()).containsExactly(m4);  // 读取映射或对象属性
    } // createWaves_groupsByThree 结束

    // 测试辅助：构造最小 MonsterConfig
    /**
     * cfg；参数：int id
     */
    private static MonsterConfig cfg(int id) {
        /**
         * 构造 MonsterConfig 实例
         */
        MonsterConfig c = new MonsterConfig();
        c.setId(id);
        c.setName("m" + id);
        c.setModelId(1);
        return c;
    } // cfg 结束
} // MonsterWaveSimpleFactoryTest 类结
