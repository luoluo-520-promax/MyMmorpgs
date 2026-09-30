package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RenewTicketCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RenewTicketScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerScRsp;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import jforgame.commons.eventbus.EventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号安全与选角业务流程：限流、封禁、二次验证标记、RENEW_TICKET、封角色选角拒绝。
 */
public class AccountSecurityFlowTest {

    private AccountRepository accountRepository;
    private PlayerRepository playerRepository;
    private PlayerSessionService sessionService;
    private PlayerEventPublisher eventPublisher;
    private SceneActorService sceneActorService;
    private PlayerTimerPersistenceService timerService;
    private EventBus eventBus;
    private AuthTokenService authTokenService;
    private LoginAdmissionManager loginAdmissionManager;
    private AccountCredentialManager credentialManager;
    private PlayerSelectionAccessManager selectionAccessManager;
    private PlayerLogoutAccessManager logoutAccessManager;
    private SessionKickService sessionKickService;
    private LoginRateLimiter loginRateLimiter;
    private LoginHistoryService loginHistoryService;
    private BanService banService;
    private AccountPlayerService service;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        accountRepository = mock(AccountRepository.class);
        playerRepository = mock(PlayerRepository.class);
        sessionService = mock(PlayerSessionService.class);
        eventPublisher = mock(PlayerEventPublisher.class);
        sceneActorService = mock(SceneActorService.class);
        timerService = mock(PlayerTimerPersistenceService.class);
        eventBus = mock(EventBus.class);
        authTokenService = mock(AuthTokenService.class);
        loginAdmissionManager = mock(LoginAdmissionManager.class);
        credentialManager = mock(AccountCredentialManager.class);
        selectionAccessManager = mock(PlayerSelectionAccessManager.class);
        logoutAccessManager = mock(PlayerLogoutAccessManager.class);
        sessionKickService = mock(SessionKickService.class);
        loginRateLimiter = mock(LoginRateLimiter.class);
        loginHistoryService = mock(LoginHistoryService.class);
        banService = mock(BanService.class);

        ObjectProvider<LoginRateLimiter> rateProvider = mock(ObjectProvider.class);
        ObjectProvider<LoginHistoryService> historyProvider = mock(ObjectProvider.class);
        ObjectProvider<BanService> banProvider = mock(ObjectProvider.class);
        ObjectProvider<cn.itcast.demo.mymmorpg.anticheat.ClientIntegrityChecker> integrityProvider =
                mock(ObjectProvider.class);
        when(rateProvider.getIfAvailable()).thenReturn(loginRateLimiter);
        when(historyProvider.getIfAvailable()).thenReturn(loginHistoryService);
        when(banProvider.getIfAvailable()).thenReturn(banService);
        when(integrityProvider.getIfAvailable()).thenReturn(null);

        service = new AccountPlayerService(
                accountRepository, playerRepository, sessionService, eventPublisher,
                sceneActorService, timerService, eventBus, authTokenService,
                loginAdmissionManager, credentialManager, selectionAccessManager,
                logoutAccessManager, sessionKickService,
                rateProvider, historyProvider, banProvider, integrityProvider,
                new ClientVersionGate(), "local");
    }

    @Test
    public void login_rateLimited_returnsLoginRateLimited() throws Exception {
        when(loginRateLimiter.isLimited(anyString(), eq("hero"))).thenReturn(true);

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero").setPassword("pwd").setClientIp("1.1.1.1").build())
                        .payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.LOGIN_RATE_LIMITED);
        verify(credentialManager, never()).verifyNormalizedName(anyString(), anyString());
    }

    @Test
    public void login_accountBanned_rejects() throws Exception {
        Account account = account(1001L, "hero");
        when(loginRateLimiter.isLimited(anyString(), anyString())).thenReturn(false);
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(banService.checkAccount(account)).thenReturn(RetCode.ACCOUNT_BANNED);

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero").setPassword("pwd").build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.ACCOUNT_BANNED);
        verify(authTokenService, never()).issueToken(anyLong(), any());
        verify(loginHistoryService).record(eq(1001L), eq("hero"), any(), eq(false), eq("ACCOUNT_BANNED"));
    }

    @Test
    public void login_newIpRisk_setsNeedSecondAuthFlag() throws Exception {
        Account account = account(1001L, "hero");
        when(loginRateLimiter.isLimited(anyString(), anyString())).thenReturn(false);
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(banService.checkAccount(account)).thenReturn(RetCode.OK);
        when(loginHistoryService.evaluateRisk(eq(1001L), any())).thenReturn("NEW_IP");
        when(authTokenService.issueToken(eq(1001L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok", System.currentTimeMillis() + 1000, List.of()));
        when(playerRepository.findByAccountIdOrderByIdAsc(1001L)).thenReturn(List.of(player(9L, 1001L)));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero").setPassword("pwd")
                        .setDeviceId("d1").setClientType("PC").setClientIp("8.8.8.8").build())
                        .payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getNeedSecondAuth()).isTrue();
        assertThat(rsp.getTokenExpireAt()).isPositive();
        verify(loginRateLimiter).clearOnSuccess(anyString(), eq("hero"));
        verify(loginHistoryService).record(eq(1001L), eq("hero"), any(), eq(true), eq("NEW_IP"));
    }

    @Test
    public void selectPlayer_playerBanned_rejects() throws Exception {
        Player p = player(9L, 1001L);
        p.setBanned(true);
        when(selectionAccessManager.validateSessionMatchesRequest(1001L, 1001L)).thenReturn(RetCode.OK);
        when(selectionAccessManager.findPlayerOwnedByAccount(9L, 1001L)).thenReturn(Optional.of(p));
        when(banService.checkPlayer(p)).thenReturn(RetCode.PLAYER_BANNED);

        SelectPlayerScRsp rsp = SelectPlayerScRsp.parseFrom(
                service.handleSelectPlayer(
                        SelectPlayerCsReq.newBuilder().setAccountId(1001L).setPlayerId(9L).build(),
                        1001L, "ws").payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.PLAYER_BANNED);
        verify(sessionService, never()).markOnline(anyLong(), any(PlayerSessionService.OnlinePresence.class));
    }

    @Test
    public void renewTicket_success_returnsNewToken() throws Exception {
        when(authTokenService.renewToken("old", "dev")).thenReturn(
                new AuthTokenService.IssueResult("new", 12345L, List.of()));

        RenewTicketScRsp rsp = RenewTicketScRsp.parseFrom(
                service.handleRenewTicket(RenewTicketCsReq.newBuilder()
                        .setToken("old").setDeviceId("dev").build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getToken()).isEqualTo("new");
        assertThat(rsp.getTokenExpireAt()).isEqualTo(12345L);
    }

    @Test
    public void renewTicket_expiredButOnline_reissuesByAccount() throws Exception {
        when(authTokenService.renewToken("dead", "dev")).thenReturn(null);
        when(authTokenService.getTokenMeta("dead")).thenReturn(
                new AuthTokenService.TokenMeta(77L, "dev", "PC", "", "", 1L, 1L));
        when(sessionService.findBoundPlayerId(77L)).thenReturn(900L);
        when(sessionService.isOnline(900L)).thenReturn(true);
        when(authTokenService.reissueByAccount(eq(77L), any())).thenReturn(
                new AuthTokenService.IssueResult("fresh", 999L, List.of()));

        RenewTicketScRsp rsp = RenewTicketScRsp.parseFrom(
                service.handleRenewTicket(RenewTicketCsReq.newBuilder()
                        .setToken("dead").setDeviceId("dev").build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getToken()).isEqualTo("fresh");
    }

    @Test
    public void login_rootedClient_rejectsUntrusted() throws Exception {
        Account account = account(1001L, "hero");
        when(loginRateLimiter.isLimited(anyString(), anyString())).thenReturn(false);
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(banService.checkAccount(account)).thenReturn(RetCode.OK);

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero")
                        .setPassword("pwd")
                        .setDeviceId("dev-1")
                        .setDeviceFingerprint("fp-1")
                        .putIntegrityFlags("rooted", true)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.CLIENT_UNTRUSTED);
        verify(authTokenService, never()).issueToken(anyLong(), any());
    }

    @Test
    public void login_cleanDevice_allowsAndIssuesToken() throws Exception {
        Account account = account(1001L, "hero");
        when(loginRateLimiter.isLimited(anyString(), anyString())).thenReturn(false);
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(banService.checkAccount(account)).thenReturn(RetCode.OK);
        when(loginHistoryService.evaluateRisk(eq(1001L), any())).thenReturn(null);
        when(authTokenService.issueToken(eq(1001L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok-clean", System.currentTimeMillis() + 1000, List.of()));
        when(playerRepository.findByAccountIdOrderByIdAsc(1001L)).thenReturn(List.of(player(9L, 1001L)));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero")
                        .setPassword("pwd")
                        .setDeviceId("dev-ok")
                        .setDeviceFingerprint("fp-ok")
                        .putIntegrityFlags("rooted", false)
                        .putIntegrityFlags("hooked", false)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getToken()).isEqualTo("tok-clean");
        assertThat(rsp.getNeedSecondAuth()).isFalse();
    }

    @Test
    public void login_emulatorSoftFlag_allowsWithSecondAuth() throws Exception {
        Account account = account(1001L, "hero");
        when(loginRateLimiter.isLimited(anyString(), anyString())).thenReturn(false);
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(banService.checkAccount(account)).thenReturn(RetCode.OK);
        when(loginHistoryService.evaluateRisk(eq(1001L), any())).thenReturn(null);
        when(authTokenService.issueToken(eq(1001L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok-emu", System.currentTimeMillis() + 1000, List.of()));
        when(playerRepository.findByAccountIdOrderByIdAsc(1001L)).thenReturn(List.of(player(9L, 1001L)));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero")
                        .setPassword("pwd")
                        .setDeviceId("dev-emu")
                        .setDeviceFingerprint("fp-emu")
                        .putIntegrityFlags("emulator", true)
                        .build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getNeedSecondAuth()).isTrue();
        assertThat(rsp.getToken()).isEqualTo("tok-emu");
    }

    @Test
    public void renewTicket_invalid_returnsNotLoggedIn() throws Exception {
        when(authTokenService.renewToken(anyString(), anyString())).thenReturn(null);
        when(authTokenService.getTokenMeta(anyString())).thenReturn(null);

        var msg = service.handleRenewTicket(RenewTicketCsReq.newBuilder()
                .setToken("x").setDeviceId("d").build());
        assertThat(msg.msgId()).isEqualTo(MessageId.RENEW_TICKET_SC_RSP);
        RenewTicketScRsp rsp = RenewTicketScRsp.parseFrom(msg.payload());
        assertThat(rsp.getRetcode()).isEqualTo(RetCode.NOT_LOGGED_IN);
    }

    private static Account account(long id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setAccountName(name);
        a.setBanned(false);
        return a;
    }

    private static Player player(long id, long accountId) {
        Player p = new Player();
        p.setId(id);
        p.setAccountId(accountId);
        p.setName("p" + id);
        p.setLevel(1);
        p.setBanned(false);
        return p;
    }
}
