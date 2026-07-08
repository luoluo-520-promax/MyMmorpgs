/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/web/SecurityController.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/web
 * 3) 主要职责：REST 暴露 RSA 公钥交换、会话 AES 密钥注册与加密上传示例（传输层另依赖 HTTPS）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.web; // player-service REST 控制器，传输层加密协商

import cn.itcast.demo.mymmorpg.support.SessionCryptoService; // 业务策略/ConfigManager/JMX 支撑
import org.springframework.http.MediaType; // HTTP Content-Type 与响应体
import org.springframework.web.bind.annotation.GetMapping; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.PostMapping; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestBody; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestMapping; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RestController; // Spring MVC REST 映射注解
import java.nio.charset.StandardCharsets; // UTF-8 解密上传明文
import java.util.Map; // REST JSON 请求/响应 Map 体
/**
 * 客户端大文件/游戏数据加密上传 HTTP 接口。
 *
 * 阶段一（非对称）：客户端 GET 公钥 -> 生成 AES 会话密钥 -> RSA 加密后 POST session-key。
 * 阶段二（对称）：客户端用 AES-GCM 加密 payload -> POST upload -> 服务端 decryptForSession。
 *
 * 传输层安全依赖 server.ssl（HTTPS），本控制器负责应用层会话密钥协商。
 */

@RestController // REST JSON 控制器
@RequestMapping("/api/security") // 控制器 URL 前缀

public class SecurityController { // SecurityController 类型定义
    private final SessionCryptoService sessionCryptoService; // RSA 密钥对 + 会话 AES 加解密
    public SecurityController(SessionCryptoService sessionCryptoService) { // 构造 SecurityController，注入 SessionCryptoService sessionCryptoService
        this.sessionCryptoService = sessionCryptoService; // 注入传输层加解密服务
    } // SecurityController 方法体结束
    /**
     * 返回服务端 RSA 公钥 Base64，客户端用于加密随机生成的 AES 会话密钥。
     */

    @GetMapping("/public-key") // HTTP GET 端点
    public Map<String, String> getPublicKey() { // 读取 PublicKey（PublicKey）
        return Map.of("publicKey", sessionCryptoService.getPublicKeyBase64()); // 客户端密钥协商第一步
    } // getPublicKey 方法体结束
    /**
     * 客户端提交 RSA 加密后的 AES 会话密钥，服务端私钥解密并与 sessionId 关联存内存/Redis。
     *
     * Body: { "sessionId": "...", "encryptedSessionKey": "Base64(...)" }
     */

    @PostMapping(path = "/session-key", consumes = MediaType.APPLICATION_JSON_VALUE) // HTTP POST 端点
    public Map<String, String> registerSessionKey(@RequestBody Map<String, String> body) throws Exception { // SecurityController.registerSessionKey：@RequestBody Map<String, String> body
        String sessionId = body.get("sessionId"); // 客户端生成的会话标识，后续 upload 携带
        String encryptedKey = body.get("encryptedSessionKey"); // RSA(publicKey, rawAesKeyBytes)
        sessionCryptoService.registerSessionKey(sessionId, encryptedKey); // 解密并缓存 AES 会话密钥
        return Map.of("status", "OK"); // 告知客户端密钥协商成功
    } // registerSessionKey 方法体结束
    /**
     * 示例加密上传：payload 为 Base64(AES_GCM(sessionKey, plainBytes))。
     *
     * Body: { "sessionId": "...", "payload": "..." }
     */

    @PostMapping(path = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE) // HTTP POST 端点
    public Map<String, Object> uploadEncrypted(@RequestBody Map<String, String> body) throws Exception { // SecurityController.uploadEncrypted：@RequestBody Map<String, String> body
        String sessionId = body.get("sessionId"); // 定位已注册的 AES 会话密钥
        String cipher = body.get("payload"); // Base64 密文
        byte[] plain = sessionCryptoService.decryptForSession(sessionId, cipher); // AES-GCM 解密上传内容
        String content = new String(plain, StandardCharsets.UTF_8); // 解密后 UTF-8 明文，供联调预览
        // TODO: 将解密后的内容作为游戏数据/文件分片写入业务存储（OSS、分片合并等）
        return Map.of( // 返回给调用方
                "status", "OK", // SecurityController 逻辑
                "receivedLength", plain.length, // 解密后字节长度
                "preview", content.length() > 64 ? content.substring(0, 64) : content // 联调预览前 64 字符
        ); // SecurityController 逻辑
    } // uploadEncrypted 方法体结束
} // SecurityController 类体结束
