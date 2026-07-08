/**
 * 文件说明
 * 模块：activity-service / 数据仓储
 * 路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/repository/ActivityRepository.java
 * 类型：接口
 * 职责：活动表（activity）的 Spring Data JPA 仓储，供 ActivityService 与 JMX 读写活动数据。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.repository; // 活动模块持久层

import cn.itcast.demo.mymmorpg.entity.Activity; // 活动实体：名称、时间窗、奖励配置、opened 开关等

import org.springframework.data.jpa.repository.JpaRepository; // 继承后 Spring 自动生成实现类

import java.util.List; // 活动列表接口返回多条记录

/**
 * 活动表 JPA 仓库接口。
 * <p>被 {@code ActivityService}、{@code ActivityServiceJmx} 注入，负责 MySQL activity 表访问。</p>
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> { // 活动主键 activityId 为 Long

    /**
     * 查询所有已开启（opened = true）的活动，用于客户端活动列表接口。
     *
     * @return 当前对外展示的活动集合；无开启活动时返回空列表
     */
    List<Activity> findByOpenedTrue(); // 方法名派生：SELECT * FROM activity WHERE opened = TRUE
}
