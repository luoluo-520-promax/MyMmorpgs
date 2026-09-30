/**
 * 文件说明：会话加解密服务单元测试。
 * 职责：验证 RSA 密钥交换后 AES-GCM 加解密往返一致性。
 */
package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.config.SessionCryptoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SessionCryptoServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SessionCryptoServiceTest.class);

    private SessionCryptoService cryptoService;

    @BeforeMethod
    public void setUp() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider =
                mock(ObjectProvider.class);
        cryptoService = new SessionCryptoService(new SessionCryptoProperties(), redisProvider);
        log.info("[测试前置] SessionCryptoService 已加载 | publicKeyLen={}",
                cryptoService.getPublicKeyBase64().length());
    }

    @Test
    public void encryptAndDecrypt_roundTrip() throws Exception {
        String sessionId = "session-10086";
        byte[] plain = "protobuf-payload-demo".getBytes(StandardCharsets.UTF_8);
        log.info("[测试开始] 场景=会话加解密往返 | sessionId={} | plainLen={}", sessionId, plain.length);

        registerSessionKeyForTest(sessionId);
        String cipherBase64 = cryptoService.encryptForSession(sessionId, plain);
        byte[] decrypted = cryptoService.decryptForSession(sessionId, cipherBase64);

        log.info("[测试断言] 场景=会话加解密往返 | cipherLen={} | decryptedLen={} | 明文一致={}",
                cipherBase64.length(), decrypted.length, java.util.Arrays.equals(plain, decrypted));
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    public void generateRandomSessionKeyBase64_hasContent() throws Exception {
        log.info("[测试开始] 场景=生成随机AES密钥");

        String keyBase64 = cryptoService.generateRandomSessionKeyBase64();

        log.info("[测试断言] 场景=生成随机AES密钥 | keyBase64Len={} | 期望>0", keyBase64.length());
        assertThat(keyBase64).isNotBlank();
    }

    private void registerSessionKeyForTest(String sessionId) throws Exception {
        String aesKeyBase64 = cryptoService.generateRandomSessionKeyBase64();
        byte[] aesKeyBytes = Base64.getDecoder().decode(aesKeyBase64);
        byte[] publicKeyBytes = Base64.getDecoder().decode(cryptoService.getPublicKeyBase64());
        var keyFactory = java.security.KeyFactory.getInstance("RSA");
        var publicKey = keyFactory.generatePublic(new java.security.spec.X509EncodedKeySpec(publicKeyBytes));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        String encryptedKeyBase64 = Base64.getEncoder().encodeToString(cipher.doFinal(aesKeyBytes));
        cryptoService.registerSessionKey(sessionId, encryptedKeyBase64);
        log.info("[测试辅助] 已完成RSA密钥交换 | sessionId={} | aesKeyLen={}", sessionId, aesKeyBytes.length);
    }
}
