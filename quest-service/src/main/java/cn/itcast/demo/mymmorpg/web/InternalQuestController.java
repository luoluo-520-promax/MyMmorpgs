/**
 * 文件维护说明
 * 1) 文件路径：quest-service/src/main/java/cn/itcast/demo/mymmorpg/web/InternalQuestController.java
 * 2) 所属模块：quest-service / web
 * 3) 主要职责：对内提供任务列表、接取任务、领取奖励三个内部 HTTP 接口，供 player-service 或测试调用。
 * 4) 变更建议：修改接口路径或协议字段时，请同步更新调用方和 proto 测试样例。
 * 5) 风险提示：该控制器直接暴露任务进度相关能力，必须依赖内网访问与鉴权约束。
 */
package cn.itcast.demo.mymmorpg.web; // quest-service 内部 HTTP 接口：任务列表、接取、领奖

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一承载 msgId 与 protobuf payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.AcceptQuestCsReq; // 接取任务请求体
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimQuestRewardCsReq; // 领取任务奖励请求体
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetQuestListCsReq; // 拉取任务列表请求体
import cn.itcast.demo.mymmorpg.service.QuestService; // 任务业务核心服务
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅 quest-service 进程启用
import org.springframework.http.MediaType; // octet-stream 请求/响应类型
import org.springframework.http.ResponseEntity; // 统一返回 byte[] 响应
import org.springframework.web.bind.annotation.PostMapping; // POST 内部接口
import org.springframework.web.bind.annotation.RequestBody; // 接收 protobuf 原始字节流
import org.springframework.web.bind.annotation.RequestHeader; // 从请求头读取玩家 ID
import org.springframework.web.bind.annotation.RequestMapping; // 统一路由前缀 /internal/quest
import org.springframework.web.bind.annotation.RestController; // 声明内部控制器

/**
 * 负责把 HTTP 请求转给 QuestService
 */
@RestController // 内部任务接口控制器，直接返回 protobuf 二进制
@ConditionalOnProperty(name = "spring.application.name", havingValue = "quest-service") // 仅任务服务实例对外提供该路由
@RequestMapping("/internal/quest") // 内部任务 API 的统一前缀
public class InternalQuestController {

    private final QuestService questService; // 任务接取、查询、领奖的业务入口

    public InternalQuestController(QuestService questService) { // 构造器注入任务服务
        this.questService = questService; // 保存任务业务服务引用，供三个接口复用
    }

    /**
     * 查询玩家可见的任务列表
     * @param playerId
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/list", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // protobuf 二进制入参与出参
    public ResponseEntity<byte[]> list(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = questService.handleGetQuestList(playerId, GetQuestListCsReq.parseFrom(body)); // 反序列化请求并交由业务层处理
        return ResponseEntity.ok(msg.payload()); // 仅返回 protobuf payload，网关层负责封装 msgId
    }

    /**
     * 玩家接取指定任务
     * @param playerId
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/accept", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // protobuf 二进制入参与出参
    public ResponseEntity<byte[]> accept(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = questService.handleAcceptQuest(playerId, AcceptQuestCsReq.parseFrom(body)); // 反序列化请求并交由业务层处理
        return ResponseEntity.ok(msg.payload()); // 直接回传接取任务响应 payload
    }

    /**
     * 玩家领取已完成任务奖励
     * @param playerId
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/claim", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // protobuf 二进制入参与出参
    public ResponseEntity<byte[]> claim(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = questService.handleClaimQuestReward(playerId, ClaimQuestRewardCsReq.parseFrom(body)); // 反序列化请求并交由业务层处理
        return ResponseEntity.ok(msg.payload()); // 直接回传领奖结果 payload
    }
}
