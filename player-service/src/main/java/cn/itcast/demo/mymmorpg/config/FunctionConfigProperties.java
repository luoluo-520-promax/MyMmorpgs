/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/FunctionConfigProperties.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：绑定 functions.list 配置项，描述各功能 id 的解锁类型（等级/任务）与参数。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import cn.itcast.demo.mymmorpg.model.ConfigFunction; // 单条功能配置：id、openType、openMainParam、openSubParam
import org.springframework.boot.context.properties.ConfigurationProperties; // 前缀 functions 对应 YAML 根键
import java.util.ArrayList; // 默认可变列表，Spring Boot 绑定 YAML 数组时 replace 元素
import java.util.List; // functions.list 在内存中的结构化表示

@ConfigurationProperties(prefix = "functions") // 例：functions.list[0].id=100, openType=1, openMainParam=10

public class FunctionConfigProperties { // FunctionConfigProperties 类型定义
    /** 功能解锁表：FunctionConfigService 启动时遍历，与 PlayerLevelUpEvent 联动自动 open */

    private List<ConfigFunction> list = new ArrayList<>(); // FunctionConfigProperties 方法
    /** Spring Boot 配置绑定回调：反序列化 YAML 数组到 ConfigFunction 列表 */

    public List<ConfigFunction> getList() { // 读取 List（List）
        return list; // FunctionConfigService 遍历全部解锁规则
    } // getList 方法体结束
    /** 测试或 @ConfigurationProperties 手动注入时使用 */

    public void setList(List<ConfigFunction> list) { // 写入 List（List）
        this.list = list != null ? list : new ArrayList<>(); // 防御 null，避免 NPE 阻断 FunctionService 初始化
    } // setList 方法体结束
} // FunctionConfigProperties 类体结束
