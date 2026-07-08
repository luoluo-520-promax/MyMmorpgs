/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/model/FunctionBox.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/model
 * 3) 主要职责：内存容器，维护玩家已开启功能 id 集合，由 FunctionBoxStore 持久化到 Redis。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.model; // player-service 功能解锁模型与配置 DTO

import java.util.Collections; // Collections，FunctionBox.java 编译依赖
import java.util.HashSet; // HashSet，FunctionBox.java 编译依赖
import java.util.Set; // Set，FunctionBox.java 编译依赖
/**
 * 玩家功能开启状态容器：FunctionService 登录时 load，解锁时 open 后 save。
 */

public class FunctionBox { // FunctionBox 类型定义
    /** 已解锁功能 id 集合，如 100=RIDE；线程安全由 FunctionService 单线程会话保证 */

    private final Set<Integer> opened = new HashSet<>(); // 玩家已解锁功能 id 去重集合
    /** 查询某功能是否已开启，供 Facade 拦截未解锁玩法 */

    public boolean isOpened(int funcId) { // 判断 Opened 是否为真
        return opened.contains(funcId); // ChatPolicy.isChannelUnlocked 等玩法准入检查
    } // isOpened 方法体结束
    /** 开启功能：add 为 false 表示已开过，FunctionService 可跳过重复 post 事件 */

    public boolean open(int funcId) { // FunctionBox.open：int funcId
        return opened.add(funcId); // newly 解锁时 add 成功，触发 PlayerFuncOpenEvent
    } // open 方法体结束
    /** 不可变快照，供 FunctionBoxStore 序列化写 Redis */

    public Set<Integer> snapshot() { // FunctionBox.snapshot：无参
        return Collections.unmodifiableSet(opened); // 持久化前冻结集合，防止外部篡改
    } // snapshot 方法体结束
    /** 从 Redis 反序列化后全量替换，login 时 load 调用 */

    public void replaceAll(Set<Integer> ids) { // FunctionBox.replaceAll：Set<Integer> ids
        opened.clear(); // 登录加载前先清空内存态
        if (ids != null) { // FunctionBox.if：ids != null
            opened.addAll(ids); // 用 Redis 中已解锁功能 id 重建内存集合
        } // if 方法体结束
    } // replaceAll 方法体结束
} // FunctionBox 类体结束
