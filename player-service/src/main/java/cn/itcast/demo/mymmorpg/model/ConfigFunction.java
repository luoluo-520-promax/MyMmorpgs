/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/model/ConfigFunction.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/model
 * 3) 主要职责：单条功能解锁配置 POJO，由 FunctionConfigProperties 映射 game.function.list 配置项。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.model; // player-service 功能解锁模型与配置 DTO

/**
 * 功能配置实体（配置驱动）：FunctionConfigService 遍历 list 判断玩家是否满足 openType/openMainParam。
 */

public class ConfigFunction { // ConfigFunction 类型定义
    /** 功能 id，对应 FunctionId 或客户端功能码 */

    private int id; // 如 100=RIDE，写入 FunctionBox 的功能编号
    /** 解锁类型，见 {@link FunctionOpenType#getType()} */

    private int openType; // 1=等级解锁，2=任务解锁
    /** 主参数：Level 时为最低等级，Quest 时为任务 id */

    private int openMainParam; // Level 型时为最低等级阈值
    /** 副参数：预留链式任务、VIP 等级等扩展条件 */

    private int openSubParam; // 预留 VIP/前置任务等复合条件
    public int getId() { // 读取 Id（Id）
        return id; // FunctionService.open 写入 FunctionBox 的功能 id
    } // getId 方法体结束

    public void setId(int id) { // 写入 Id（Id）
        this.id = id; // YAML functions.list[].id 映射
    } // setId 方法体结束

    public int getOpenType() { // 读取 OpenType（OpenType）
        return openType; // FunctionService.checkOpen 过滤解锁类型
    } // getOpenType 方法体结束

    public void setOpenType(int openType) { // 写入 OpenType（OpenType）
        this.openType = openType; // YAML functions.list[].openType 映射
    } // setOpenType 方法体结束

    public int getOpenMainParam() { // 读取 OpenMainParam（OpenMainParam）
        return openMainParam; // Level 型与 player.level 比较
    } // getOpenMainParam 方法体结束

    public void setOpenMainParam(int openMainParam) { // 写入 OpenMainParam（OpenMainParam）
        this.openMainParam = openMainParam; // YAML functions.list[].openMainParam 映射
    } // setOpenMainParam 方法体结束

    public int getOpenSubParam() { // 读取 OpenSubParam（OpenSubParam）
        return openSubParam; // 预留扩展条件参数
    } // getOpenSubParam 方法体结束

    public void setOpenSubParam(int openSubParam) { // 写入 OpenSubParam（OpenSubParam）
        this.openSubParam = openSubParam; // YAML functions.list[].openSubParam 映射
    } // setOpenSubParam 方法体结束
} // ConfigFunction 类体结束
