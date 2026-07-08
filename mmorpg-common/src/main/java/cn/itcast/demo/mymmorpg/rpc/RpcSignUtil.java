/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcSignUtil.java
 * 类型：类
 * 职责：跨服 RPC 握手签名的生成与校验（MD5）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 跨服握手签名工具：MD5(serverId + "_" + signKey) 十六进制小写。
 * <p>
 * 游戏服连接战斗服/中心服时，在 {@link RpcReqServerLogin} 等握手中携带 sign，
 * 对端用相同算法校验，防止未授权节点接入 MMORPG 集群 RPC 网络。
 * </p>
 */
public final class RpcSignUtil {

    /** 工具类禁止实例化 */
    private RpcSignUtil() {
    }

    /**
     * 根据区服 ID 与配置密钥生成握手签名。
     *
     * @param serverId 本节点在集群中的 serverId（游戏服/战斗服/中心服唯一编号）
     * @param signKey  配置项 rpc.sign-key，集群内共享
     * @return MD5 十六进制小写字符串，写入 RPC 登录请求
     */
    public static String sign(int serverId, String signKey) {
        String raw = serverId + "_" + signKey; // 拼接明文：区服身份 + 共享密钥
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)); // UTF-8 字节做 MD5，作为跨服互信凭证
    }

    /**
     * 校验对端握手签名是否与本地预期一致。
     *
     * @param signKey  本地配置的 rpc.sign-key
     * @param serverId 对端声明的 serverId
     * @param sign     对端 RpcReqServerLogin 中携带的 sign 字段
     * @return true 表示签名合法，允许建立跨服 RPC 长连接
     */
    public static boolean verify(String signKey, int serverId, String sign) {
        if (sign == null || signKey == null) { // 缺参无法验签，拒绝握手
            return false;
        }
        return sign.equalsIgnoreCase(sign(serverId, signKey)); // 忽略大小写比较 MD5 十六进制
    }
}
