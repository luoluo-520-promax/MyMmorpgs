/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/client/ActivityCommandClient.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/client
 * 3) 主要职责：Feign 声明式客户端，微服务模式下 HTTP 调用 activity-service 内部活动 API。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.client; // player-service Feign 客户端，远程调用 battle/activity

import org.springframework.cloud.openfeign.FeignClient; // Feign 远程调用 FeignClient
import org.springframework.http.MediaType; // HTTP Content-Type 与响应体
import org.springframework.web.bind.annotation.PostMapping; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestBody; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestHeader; // Spring MVC REST 映射注解
/**
 * player-service -> activity-service 内部活动命令 Feign 客户端。
 * 微服务模式下由 RemoteActivityGateway 注入使用。
 */

@FeignClient(name = "activity-service", contextId = "activityCommandClient") // Nacos 服务名 activity-service

public interface ActivityCommandClient { // ActivityCommandClient 接口定义
    /**
     * 查询玩家可见活动列表：body 为 GetActivityListCsReq，响应 ActivityBriefInfo 列表。
     */

    @PostMapping(value = "/internal/activity/list", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // ActivityCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // ActivityCommandClient 逻辑
    byte[] list(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程拉取活动列表
    /**
     * 查询单个活动详情与进度：body 含 activityId。
     */

    @PostMapping(value = "/internal/activity/detail", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // ActivityCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // ActivityCommandClient 逻辑
    byte[] detail(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程查活动详情
    /**
     * 领取活动奖励：body 含 activityId 与 tierIndex，activity-service 落库并写 retcode。
     */

    @PostMapping(value = "/internal/activity/claim", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // ActivityCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // ActivityCommandClient 逻辑
    byte[] claim(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程领取活动奖励
} // ActivityCommandClient 类体结束
