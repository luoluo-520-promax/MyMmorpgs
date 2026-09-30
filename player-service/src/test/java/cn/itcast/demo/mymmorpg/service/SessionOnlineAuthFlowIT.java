package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import cn.itcast.demo.mymmorpg.test.support.RedisITSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 新会话/在线/多端登录 Redis 真流程：签发 → 多端并存 → 踢最老 → 续票 → 在线 Hash。
 */
public class SessionOnlineAuthFlowIT {

    private RedisITSupport redis;
    private SessionLoginProperties props;
    private AuthTokenService authTokenService;
    private PlayerSessionService playerSessionService;
    private LoginRateLimiter loginRateLimiter;

    @BeforeMethod
    public void setUp() {
        redis = RedisITSupport.start();
        props = new SessionLoginProperties();
        props.setTokenTtlHours(2);
        props.setLoginRateWindowSeconds(60);
        props.setLoginRateMaxAttempts(3);
        props.setOnlineHeartbeatTimeoutSeconds(180);
        ObjectProvider<ObjectMapper> om = mock(ObjectProvider.class);
        when(om.getIfAvailable()).thenReturn(new ObjectMapper());
        authTokenService = new AuthTokenService(redis.redis(), om, props, 10000);
        playerSessionService = new PlayerSessionService(redis.redis(), props, "node-a");
        loginRateLimiter = new LoginRateLimiter(redis.redis(), props);
    }

    @AfterMethod
    public void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    public void multiDeviceAllowlist_pcAndMobile_coexist_sameTypeKicks() {
        props.setLoginPolicy("multi_device_allowlist");
        var pc1 = authTokenService.issueToken(100L, new AuthTokenService.LoginDeviceContext("pc-1", "PC", "1.1.1.1", "ua"));
        var mobile = authTokenService.issueToken(100L, new AuthTokenService.LoginDeviceContext("m-1", "MOBILE", "2.2.2.2", "ua"));
        assertThat(authTokenService.listSessions(100L)).hasSize(2);
        assertThat(authTokenService.getAccountIdByToken(pc1.token())).isEqualTo(100L);
        assertThat(authTokenService.getAccountIdByToken(mobile.token())).isEqualTo(100L);

        var pc2 = authTokenService.issueToken(100L, new AuthTokenService.LoginDeviceContext("pc-2", "PC", "1.1.1.1", "ua"));
        assertThat(pc2.kickedDeviceIds()).contains("pc-1");
        assertThat(authTokenService.getAccountIdByToken(pc1.token())).isNull();
        assertThat(authTokenService.getAccountIdByToken(pc2.token())).isEqualTo(100L);
        assertThat(authTokenService.getAccountIdByToken(mobile.token())).isEqualTo(100L);
        assertThat(authTokenService.listSessions(100L)).hasSize(2);
    }

    @Test
    public void kickOldest_evictsEarliestWhenExceedMaxSessions() throws Exception {
        props.setLoginPolicy("kick_oldest");
        props.setMaxSessions(2);
        var a = authTokenService.issueToken(200L, new AuthTokenService.LoginDeviceContext("d1", "PC", "", ""));
        Thread.sleep(5);
        var b = authTokenService.issueToken(200L, new AuthTokenService.LoginDeviceContext("d2", "MOBILE", "", ""));
        Thread.sleep(5);
        var c = authTokenService.issueToken(200L, new AuthTokenService.LoginDeviceContext("d3", "CONSOLE", "", ""));
        assertThat(c.kickedDeviceIds()).contains("d1");
        assertThat(authTokenService.getAccountIdByToken(a.token())).isNull();
        assertThat(authTokenService.getAccountIdByToken(b.token())).isEqualTo(200L);
        assertThat(authTokenService.getAccountIdByToken(c.token())).isEqualTo(200L);
        assertThat(authTokenService.listSessions(200L)).hasSize(2);
    }

    @Test
    public void singleDevice_secondLoginRevokesFirst() {
        props.setLoginPolicy("single_device");
        var first = authTokenService.issueToken(300L, new AuthTokenService.LoginDeviceContext("a", "PC", "", ""));
        var second = authTokenService.issueToken(300L, new AuthTokenService.LoginDeviceContext("b", "MOBILE", "", ""));
        assertThat(authTokenService.getAccountIdByToken(first.token())).isNull();
        assertThat(authTokenService.getAccountIdByToken(second.token())).isEqualTo(300L);
        assertThat(authTokenService.listSessions(300L)).hasSize(1);
    }

    @Test
    public void renewTicket_rotatesTokenAndKeepsAccount() {
        props.setLoginPolicy("single_device");
        var issued = authTokenService.issueToken(400L, new AuthTokenService.LoginDeviceContext("dev", "PC", "9.9.9.9", "ua"));
        var renewed = authTokenService.renewToken(issued.token(), "dev");
        assertThat(renewed).isNotNull();
        assertThat(renewed.token()).isNotEqualTo(issued.token());
        assertThat(authTokenService.getAccountIdByToken(issued.token())).isNull();
        assertThat(authTokenService.getAccountIdByToken(renewed.token())).isEqualTo(400L);
        AuthTokenService.TokenMeta meta = authTokenService.getTokenMeta(renewed.token());
        assertThat(meta.deviceId()).isEqualTo("dev");
        assertThat(meta.clientType()).isEqualTo("PC");
    }

    @Test
    public void onlineHash_markTouchUpdateSweepAndStats() {
        playerSessionService.markOnline(9001L, PlayerSessionService.OnlinePresence.of(
                "ws-1", "node-a", 3, "10.0.0.1", "dev-x", "PC"));
        assertThat(playerSessionService.isOnline(9001L)).isTrue();
        assertThat(playerSessionService.countOnline()).isEqualTo(1);
        assertThat(playerSessionService.countOnlineOnNode("node-a")).isEqualTo(1);

        Map<String, String> fields = playerSessionService.getOnlineFields(9001L);
        assertThat(fields.get("session_id")).isEqualTo("ws-1");
        assertThat(fields.get("scene_id")).isEqualTo("3");
        assertThat(fields.get("device_id")).isEqualTo("dev-x");
        assertThat(fields.get("last_heartbeat")).isNotBlank();

        playerSessionService.updateScene(9001L, 7, "node-b");
        assertThat(playerSessionService.countOnlineOnNode("node-a")).isEqualTo(0);
        assertThat(playerSessionService.countOnlineOnNode("node-b")).isEqualTo(1);
        assertThat(playerSessionService.getOnlineFields(9001L).get("scene_id")).isEqualTo("7");

        playerSessionService.touchHeartbeat(9001L);
        long hb1 = Long.parseLong(playerSessionService.getOnlineFields(9001L).get("last_heartbeat"));
        assertThat(hb1).isPositive();

        // 人为把心跳写旧，触发清扫
        redis.redis().opsForHash().put("online:user:9001", "last_heartbeat",
                String.valueOf(System.currentTimeMillis() - 600_000L));
        assertThat(playerSessionService.sweepStaleSessions()).isEqualTo(1);
        assertThat(playerSessionService.isOnline(9001L)).isFalse();
        assertThat(playerSessionService.countOnline()).isEqualTo(0);
    }

    @Test
    public void loginRateLimiter_blocksAfterMaxAttempts_thenClearsOnSuccess() {
        assertThat(loginRateLimiter.isLimited("1.2.3.4", "hero")).isFalse();
        assertThat(loginRateLimiter.isLimited("1.2.3.4", "hero")).isFalse();
        assertThat(loginRateLimiter.isLimited("1.2.3.4", "hero")).isFalse();
        assertThat(loginRateLimiter.isLimited("1.2.3.4", "hero")).isTrue();
        loginRateLimiter.clearOnSuccess("1.2.3.4", "hero");
        assertThat(loginRateLimiter.isLimited("1.2.3.4", "hero")).isFalse();
    }

    @Test
    public void sameDeviceRelogin_replacesTokenWithoutKickingOthers() {
        props.setLoginPolicy("multi_device_allowlist");
        var pc = authTokenService.issueToken(500L, new AuthTokenService.LoginDeviceContext("pc", "PC", "", ""));
        var mobile = authTokenService.issueToken(500L, new AuthTokenService.LoginDeviceContext("phone", "MOBILE", "", ""));
        var pcAgain = authTokenService.issueToken(500L, new AuthTokenService.LoginDeviceContext("pc", "PC", "", ""));
        assertThat(pcAgain.kickedDeviceIds()).isEmpty();
        assertThat(authTokenService.getAccountIdByToken(pc.token())).isNull();
        assertThat(authTokenService.getAccountIdByToken(pcAgain.token())).isEqualTo(500L);
        assertThat(authTokenService.getAccountIdByToken(mobile.token())).isEqualTo(500L);
    }
}
