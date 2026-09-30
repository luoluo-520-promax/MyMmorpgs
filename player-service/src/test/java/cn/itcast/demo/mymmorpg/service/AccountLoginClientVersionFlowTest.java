package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import jforgame.commons.eventbus.EventBus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录业务流程：Client-Version 强制校验与推荐更新标记。
 */
public class AccountLoginClientVersionFlowTest {

    private AccountRepository accountRepository;
    private PlayerRepository playerRepository;
    private AuthTokenService authTokenService;
    private LoginAdmissionManager loginAdmissionManager;
    private AccountCredentialManager credentialManager;
    private SessionKickService sessionKickService;
    private PlayerEventPublisher eventPublisher;
    private ClientVersionGate clientVersionGate;
    private AccountPlayerService service;

    @BeforeMethod
    public void setUp() {
        accountRepository = mock(AccountRepository.class);
        playerRepository = mock(PlayerRepository.class);
        authTokenService = mock(AuthTokenService.class);
        loginAdmissionManager = mock(LoginAdmissionManager.class);
        credentialManager = mock(AccountCredentialManager.class);
        sessionKickService = mock(SessionKickService.class);
        eventPublisher = mock(PlayerEventPublisher.class);
        clientVersionGate = new ClientVersionGate();
        clientVersionGate.setMinClientVersion(10000);
        clientVersionGate.setOptionalClientVersion(12000);

        @SuppressWarnings("unchecked")
        ObjectProvider empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        service = new AccountPlayerService(
                accountRepository, playerRepository,
                mock(PlayerSessionService.class), eventPublisher,
                mock(SceneActorService.class), mock(PlayerTimerPersistenceService.class),
                mock(EventBus.class), authTokenService,
                loginAdmissionManager, credentialManager,
                mock(PlayerSelectionAccessManager.class),
                mock(PlayerLogoutAccessManager.class),
                sessionKickService,
                empty, empty, empty, empty,
                clientVersionGate,
                "local");
    }

    @Test
    public void login_rejectsWhenClientVersionTooOld() throws Exception {
        Account account = account(2001L, "old-client");
        when(loginAdmissionManager.checkBeforeCredential("old-client")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("old-client", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("old-client")
                        .setPassword("pwd")
                        .setClientVersionNumber(8000)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.CLIENT_VERSION_TOO_OLD);
        verify(authTokenService, never()).issueToken(eq(2001L), any());
    }

    @Test
    public void login_succeedsWithRecommendUpdateFlag() throws Exception {
        Account account = account(2002L, "mid-client");
        when(loginAdmissionManager.checkBeforeCredential("mid-client")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("mid-client", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(authTokenService.issueToken(eq(2002L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok-v", System.currentTimeMillis() + 3600_000L, List.of()));
        when(playerRepository.findByAccountIdOrderByIdAsc(2002L)).thenReturn(List.of(player(21L, 2002L)));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("mid-client")
                        .setPassword("pwd")
                        .setClientVersionNumber(11000)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getRecommendClientUpdate()).isTrue();
        assertThat(rsp.getToken()).isEqualTo("tok-v");
        verify(sessionKickService).kickPreviousSession(eq(2002L), any());
    }

    @Test
    public void login_succeedsWithoutRecommendWhenUpToDate() throws Exception {
        Account account = account(2003L, "new-client");
        when(loginAdmissionManager.checkBeforeCredential("new-client")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("new-client", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(authTokenService.issueToken(eq(2003L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok-n", System.currentTimeMillis() + 3600_000L, List.of()));
        when(playerRepository.findByAccountIdOrderByIdAsc(2003L)).thenReturn(List.of());

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("new-client")
                        .setPassword("pwd")
                        .setClientVersionNumber(13000)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getRecommendClientUpdate()).isFalse();
    }

    private static Account account(long id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setAccountName(name);
        return a;
    }

    private static Player player(long id, long accountId) {
        Player p = new Player();
        p.setId(id);
        p.setAccountId(accountId);
        p.setName("p" + id);
        p.setLevel(1);
        return p;
    }
}
