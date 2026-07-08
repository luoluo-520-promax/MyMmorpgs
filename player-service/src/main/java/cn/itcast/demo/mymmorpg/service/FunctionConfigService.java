/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/FunctionConfigService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：功能解锁配置索引，按 openType 快速查询 YAML 中的 ConfigFunction 列表。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // game.function.list YAML 配置 openType 内存索引

import cn.itcast.demo.mymmorpg.model.ConfigFunction; // game.function.list 中单条功能解锁配置
import cn.itcast.demo.mymmorpg.config.FunctionConfigProperties; // 读取 game.function.* YAML 配置项
import org.springframework.stereotype.Service; // FunctionService.checkOpen 注入本类查配置

import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // 启动时构建索引，运行时只读并发安全

/**
 * 功能配置查询：提供按 openType 的索引查询能力。
 */
@Service // 功能解锁逻辑 FunctionService 依赖本 openType 索引
public class FunctionConfigService { // 启动时从 game.function.list 构建 openType→ConfigFunction 索引

    /** 读取 game.function.list YAML 功能解锁配置项 */
    private final FunctionConfigProperties props; // @ConfigurationProperties game.function.*

    /** openType（如等级解锁=1）→ 该类型下全部 ConfigFunction 的内存索引 */
    private final Map<Integer, List<ConfigFunction>> byOpenType = new ConcurrentHashMap<>(); // Level=1/Quest=2 分组索引

    public FunctionConfigService(FunctionConfigProperties props) { // 功能配置查询：提供按 openType 的索引查询能力
        this.props = props; // 读取 game.function.list YAML 功能解锁配置
        rebuildIndex(); // 启动时按 openType 分组构建内存索引
    }

    /**
     * 按解锁类型查询功能配置列表，如 Level=1、Quest=2。
     */
    public Collection<ConfigFunction> queryByOpenType(int openType) { // 按解锁类型查询功能配置列表，如 Level=1、Quest=2
        return byOpenType.getOrDefault(openType, Collections.emptyList()); // 无该 openType 配置时返回空集合
    }

    /** 从 props.list 重建 openType 索引，配置热更时可再次调用 */
    private void rebuildIndex() { // 从 props.list 重建 openType 索引，配置热更时可再次调用
        byOpenType.clear(); // 清空旧 openType 索引
        if (props.getList() == null) { // game.function.list YAML 未配置
            return; // 索引为空，FunctionService.checkOpen 无待检功能
        }
        for (ConfigFunction f : props.getList()) { // 遍历 game.function.list 每条 ConfigFunction
            byOpenType.computeIfAbsent(f.getOpenType(), k -> new ArrayList<>()).add(f); // 按 openType 分组入 ConcurrentHashMap
        }
    }
}
