package cn.itcast.demo.mymmorpg.support;

import cn.itcast.demo.mymmorpg.config.SessionCryptoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class SessionCryptoService {

    private static final Logger log = LoggerFactory.getLogger(SessionCryptoService.class);

    private static final String RSA_ALGO = "RSA";
    private static final String AES_ALGO = "AES";
    private static final String AES_GCM_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final KeyPair rsaKeyPair;
    private final SessionKeyStore sessionKeyStore;

    public SessionCryptoService(SessionCryptoProperties properties,
                                ObjectProvider<StringRedisTemplate> redisTemplate) throws Exception {
        this.rsaKeyPair = loadOrGenerateKeyPair(properties);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            this.sessionKeyStore = new RedisSessionKeyStore(redis, properties.getSessionTtl());
            log.info("会话 AES 密钥存储：Redis（TTL={}）", properties.getSessionTtl());
        } else {
            this.sessionKeyStore = new InMemorySessionKeyStore();
            log.warn("会话 AES 密钥存储：进程内存（多节点不可用，建议配置 Redis）");
        }
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
    }

    public void registerSessionKey(String sessionId, String encryptedKeyBase64) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(encryptedKeyBase64);
        Cipher cipher = Cipher.getInstance(RSA_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
        byte[] keyBytes = cipher.doFinal(encrypted);
        sessionKeyStore.put(sessionId, keyBytes);
    }

    public String generateRandomSessionKeyBase64() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGO);
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private SecretKey getAesKeyForSession(String sessionId) {
        byte[] key = sessionKeyStore.get(sessionId);
        if (key == null) {
            throw new IllegalStateException("会话尚未完成密钥交换，sessionId=" + sessionId);
        }
        return new SecretKeySpec(key, AES_ALGO);
    }

    public String encryptForSession(String sessionId, byte[] plain) throws Exception {
        SecretKey key = getAesKeyForSession(sessionId);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM_ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] cipherText = cipher.doFinal(plain);

        ByteBuffer bb = ByteBuffer.allocate(iv.length + cipherText.length);
        bb.put(iv);
        bb.put(cipherText);
        return Base64.getEncoder().encodeToString(bb.array());
    }

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
        return cipher.doFinal(cipherText);
    }

    private static KeyPair loadOrGenerateKeyPair(SessionCryptoProperties properties) throws Exception {
        String privB64 = properties.getRsaPrivateKeyBase64();
        String pubB64 = properties.getRsaPublicKeyBase64();
        if (privB64 != null && !privB64.isBlank() && pubB64 != null && !pubB64.isBlank()) {
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGO);
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privB64)));
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)));
            log.info("RSA 密钥对已加载（外置配置）");
            return new KeyPair(publicKey, privateKey);
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_ALGO);
        kpg.initialize(2048);
        KeyPair generated = kpg.generateKeyPair();
        log.warn("RSA 密钥对为本次启动临时生成，重启后客户端需重新协商；生产请配置 game.session-crypto.rsa-*-key-base64");
        return generated;
    }
}
