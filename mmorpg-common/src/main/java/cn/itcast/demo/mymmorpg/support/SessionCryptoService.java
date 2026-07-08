/**
 * 客户端-服务端会话加解密服务：MMORPG 长连接建立后，先用 RSA 交换 AES 会话密钥，
 * 再用 AES-GCM 加密大包体（如场景快照、批量协议），降低 RSA 性能开销。
 */
package cn.itcast.demo.mymmorpg.support;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher; // JCE 加解密入口
import javax.crypto.KeyGenerator; // 生成 AES 对称密钥
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec; // AES-GCM 模式参数（IV + 认证标签长度）
import javax.crypto.spec.SecretKeySpec; // 从字节数组还原 AES 密钥

import java.nio.ByteBuffer; // 拼接 IV 与密文为连续字节
import java.security.KeyPair; // RSA 公钥/私钥对
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64; // 密钥与密文 Base64 传输（协议层常用）
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 多连接并发注册 sessionKey

/**
 * 会话级加解密：
 * 1. 服务端持 RSA 私钥，客户端用公钥加密随机 AES 密钥完成密钥交换；
 * 2. 后续游戏协议大包用 AES-GCM 对称加密，防篡改且性能优于 RSA。
 * 演示环境 sessionKey 存进程内存；生产可迁 Redis 以支持多节点踢线/续期。
 */
@Service
public class SessionCryptoService {

    private static final String RSA_ALGO = "RSA"; // 非对称算法，用于密钥交换
    private static final String AES_ALGO = "AES"; // 对称算法名
    private static final String AES_GCM_ALGO = "AES/GCM/NoPadding"; // 带认证标签的 AES 模式
    private static final int GCM_TAG_LENGTH = 128; // GCM 认证标签 128 bit
    private static final int GCM_IV_LENGTH = 12; // GCM 推荐 IV 长度 12 字节

    private final KeyPair rsaKeyPair; // 服务启动时生成，公钥下发给客户端
    /** sessionId（Netty Channel 或登录会话）-> AES 密钥字节 */
    private final Map<String, byte[]> sessionKeys = new ConcurrentHashMap<>();

    public SessionCryptoService() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_ALGO);
        kpg.initialize(2048); // 2048 位 RSA，兼顾安全与性能
        this.rsaKeyPair = kpg.generateKeyPair();
    }

    /** 返回 Base64 编码的 RSA 公钥，客户端用于加密 AES 会话密钥 */
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
    }

    /**
     * 客户端用服务端公钥加密 AES 密钥后上传，服务端私钥解密并绑定 sessionId。
     *
     * @param encryptedKeyBase64 RSA 加密后的 AES 密钥（Base64）
     */
    public void registerSessionKey(String sessionId, String encryptedKeyBase64) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedKeyBase64);
        Cipher cipher = Cipher.getInstance(RSA_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate()); // 私钥解密得到 AES key bytes
        byte[] keyBytes = cipher.doFinal(encrypted);
        sessionKeys.put(sessionId, keyBytes); // 后续 encrypt/decrypt 按 sessionId 取密钥
    }

    /** 服务端生成随机 AES 密钥（Base64），供测试或客户端不自行生成时使用 */
    public String generateRandomSessionKeyBase64() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGO);
        keyGen.init(256); // AES-256
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** 按 sessionId 取 AES SecretKey；未完成密钥交换则抛异常，阻止明文冒充加密包 */
    private SecretKey getAesKeyForSession(String sessionId) {
        byte[] key = sessionKeys.get(sessionId);
        if (key == null) {
            throw new IllegalStateException("会话尚未完成密钥交换，sessionId=" + sessionId);
        }
        return new SecretKeySpec(key, AES_ALGO);
    }

    /**
     * AES-GCM 加密：随机 IV 前置，密文含认证标签；整体 Base64 便于协议传输。
     *
     * @param plain 明文协议字节（如 protobuf 序列化结果）
     */
    public String encryptForSession(String sessionId, byte[] plain) throws Exception {
        SecretKey key = getAesKeyForSession(sessionId);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom(); // 密码学安全随机 IV
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] cipherText = cipher.doFinal(plain); // 含 GCM 认证 tag

        ByteBuffer bb = ByteBuffer.allocate(iv.length + cipherText.length);
        bb.put(iv); // 解密端先读 IV
        bb.put(cipherText);
        return Base64.getEncoder().encodeToString(bb.array());
    }

    /**
     * AES-GCM 解密：从 Base64 解析 IV + 密文，验证 tag 后返回明文。
     */
    public byte[] decryptForSession(String sessionId, String cipherBase64) throws Exception {
        SecretKey key = getAesKeyForSession(sessionId);
        byte[] all = Base64.getDecoder().decode(cipherBase64);
        ByteBuffer bb = ByteBuffer.wrap(all);

        byte[] iv = new byte[GCM_IV_LENGTH];
        bb.get(iv);
        byte[] cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(cipherText); // tag 校验失败会抛 AEADBadTagException
    }
}
