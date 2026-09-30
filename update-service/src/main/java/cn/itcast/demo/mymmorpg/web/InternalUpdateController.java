package cn.itcast.demo.mymmorpg.web; // 更新服务内部接口层，向 player-service 提供版本查询与校验能力

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 协议消息封装体，用于提取最终 payload 返回给 HTTP 调用方
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetVersionManifestCsReq; // 拉取版本清单的请求协议
import cn.itcast.demo.mymmorpg.protocol.protobuf.VerifyResourceChecksumCsReq; // 资源校验请求协议
import cn.itcast.demo.mymmorpg.service.UpdateService; // 更新核心服务，用于处理版本清单和 checksum 校验
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 限定该控制器只在 update-service 中启用
import org.springframework.web.bind.annotation.PostMapping; // 暴露 POST 接口承接 protobuf 二进制请求
import org.springframework.web.bind.annotation.RequestBody; // 从请求体读取原始 protobuf bytes
import org.springframework.web.bind.annotation.RequestMapping; // 配置统一路由前缀
import org.springframework.web.bind.annotation.RestController; // 声明为 REST 控制器并直接返回响应体

/**
 * 更新服务内部 REST API，供 player-service Feign 或运维工具调用。
 */
@RestController // 将内部更新接口暴露为 HTTP REST 服务
@RequestMapping("/internal/update") // 统一的内部更新路由前缀，便于网关或 Feign 定位
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service") // 只在更新服务进程内注册该控制器
public class InternalUpdateController { // 版本清单和资源校验的内部 HTTP 入口

    private final UpdateService updateService; // 核心更新服务，实际处理业务逻辑

    public InternalUpdateController(UpdateService updateService) {
        this.updateService = updateService; // 注入更新服务，供两个内部接口复用
    }

    /**
     * 客户端拉取版本清单时调用的内部接口
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping("/manifest")
    public byte[] manifest(@RequestBody byte[] body) throws Exception { // 直接接收 protobuf 二进制，避免额外转换损耗
        ProtocolMessage msg = updateService.handleGetVersionManifest(GetVersionManifestCsReq.parseFrom(body)); // 解析请求并交给核心服务计算补丁计划
        return msg.payload(); // 返回协议消息的原始 payload，供调用方继续转发
    }

    /**
     * 客户端上报本地 checksum 校验结果时调用的内部接口
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping("/verify")
    public byte[] verify(@RequestBody byte[] body) throws Exception { // 直接接收 protobuf 二进制，保持与游戏协议一致
        ProtocolMessage msg = updateService.handleVerifyResourceChecksum(VerifyResourceChecksumCsReq.parseFrom(body)); // 解析请求并执行本地文件完整性校验
        return msg.payload(); // 返回修复文件列表和异常路径给客户端
    }
}
