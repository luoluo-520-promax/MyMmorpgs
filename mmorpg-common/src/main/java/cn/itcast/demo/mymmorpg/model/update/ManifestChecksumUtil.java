package cn.itcast.demo.mymmorpg.model.update; // 更新清单 checksum 计算工具，负责 SHA-256 摘要与比对

import java.nio.charset.StandardCharsets; // 指定字符串转字节时使用 UTF-8 编码
import java.security.MessageDigest; // Java 加密摘要工具，用于生成 SHA-256
import java.security.NoSuchAlgorithmException; // 当运行环境缺少 SHA-256 算法时抛出
import java.util.HexFormat; // 将摘要字节转换为十六进制字符串

/**
 * 资源清单与文件 SHA-256 校验工具。
 */
public final class ManifestChecksumUtil { // checksum 计算与比较的静态工具类

    private ManifestChecksumUtil() { // 禁止实例化，确保仅通过静态方法使用
    }

    public static String sha256Hex(byte[] data) { // 对原始字节数组计算 SHA-256 十六进制摘要
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // 获取 SHA-256 摘要器
            return HexFormat.of().formatHex(digest.digest(data)).toLowerCase(); // 输出统一的小写十六进制摘要
        } catch (NoSuchAlgorithmException e) { // 运行环境不支持 SHA-256 时属于严重配置问题
            throw new IllegalStateException("SHA-256 not available", e); // 抛出非法状态异常，提示环境异常
        }
    }

    public static String sha256Hex(String text) { // 对字符串内容按 UTF-8 计算 SHA-256 摘要
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8)); // 统一通过字节数组方法完成计算
    }

    /**
     * 比较客户端本地 checksum 与服务端期望值（忽略大小写）。
     */
    public static boolean matches(String expected, String actual) { // 比较两个 checksum 是否一致，忽略大小写差异
        if (expected == null || actual == null) { // 任一 checksum 为空都视为不匹配
            return false; // 直接返回不一致，避免继续误判
        }
        return expected.equalsIgnoreCase(actual.trim()); // 去除客户端多余空白后再按忽略大小写规则比较
    }
}
