package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.event.PlayerDataPreloadEvent;
import cn.itcast.demo.mymmorpg.event.PlayerLoginEvent;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerScRsp;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import jforgame.commons.eventbus.EventBus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 大世界二游核心链路：账号登录 / 选角 / 登出与运行时拆除。
 */
public class AccountPlayerServiceTest {

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
    private AccountPlayerService service;

    @BeforeMethod
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
        service = new AccountPlayerService(
                accountRepository, playerRepository, sessionService, eventPublisher,
                sceneActorService, timerService, eventBus, authTokenService,
                loginAdmissionManager, credentialManager, selectionAccessManager,
                logoutAccessManager, sessionKickService);
    }

    @Test
    public void login_success_issuesTokenAndKicksPrevious() throws Exception {
        Account account = account(1001L, "hero");
        when(loginAdmissionManager.checkBeforeCredential("hero")).thenReturn(RetCode.OK);
        when(credentialManager.verifyNormalizedName("hero", "pwd"))
                .thenReturn(AccountCredentialOutcome.ok(account));
        when(authTokenService.issueToken(eq(1001L), any())).thenReturn(
                new AuthTokenService.IssueResult("tok-abc", System.currentTimeMillis() + 7200_000L, List.of()));
        when(authTokenService.tokenExpireAtMillis()).thenReturn(System.currentTimeMillis() + 7200_000L);
        Player p = player(9L, 1001L, "角色A", 10);
        when(playerRepository.findByAccountIdOrderByIdAsc(1001L)).thenReturn(List.of(p));

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero").setPassword("pwd").build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getAccountId()).isEqualTo(1001L);
        assertThat(rsp.getToken()).isEqualTo("tok-abc");
        assertThat(rsp.getPlayerListCount()).isEqualTo(1);
        verify(sessionKickService).kickPreviousSession(eq(1001L), any());
        verify(accountRepository).save(account);
        verify(eventPublisher).publishAccountLogin(1001L, "hero");
    }

    @Test
    public void login_admissionRejected_skipsCredential() throws Exception {
        when(loginAdmissionManager.checkBeforeCredential("hero"))
                .thenReturn(RetCode.SERVER_OVERLOADED);

        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(
                service.handleAccountLogin(AccountLoginCsReq.newBuilder()
                        .setAccountName("hero").setPassword("pwd").build()).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.SERVER_OVERLOADED);
        verifyNoInteractions(credentialManager);
        verify(authTokenService, never()).issueToken(anyLong(), any());
    }

    @Test
    public void selectPlayer_success_marksOnlineAndPublishesEvents() throws Exception {
        Player p = player(9L, 1001L, "角色A", 10);
        when(selectionAccessManager.validateSessionMatchesRequest(1001L, 1001L)).thenReturn(RetCode.OK);
        when(selectionAccessManager.findPlayerOwnedByAccount(9L, 1001L)).thenReturn(Optional.of(p));

        SelectPlayerScRsp rsp = SelectPlayerScRsp.parseFrom(
                service.handleSelectPlayer(
                        SelectPlayerCsReq.newBuilder().setAccountId(1001L).setPlayerId(9L).build(),
                        1001L, "ws-1").payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        assertThat(rsp.getPlayerInfo().getPlayerId()).isEqualTo(9L);
        verify(sessionService).markOnline(eq(9L), any(PlayerSessionService.OnlinePresence.class));
        verify(sessionService).bindAccountPlayer(1001L, 9L);
        verify(timerService).startPlayerTimer(9L);
        verify(eventPublisher).publishPlayerEnter(1001L, 9L, "角色A");
        verify(eventBus).publish(any(PlayerLoginEvent.class));
        verify(eventBus).publish(any(PlayerDataPreloadEvent.class));
    }

    @Test
    public void logout_withSelectedPlayer_tearsDownWorldRuntime() throws Exception {
        when(logoutAccessManager.validateAccountSession(1001L)).thenReturn(RetCode.OK);
        when(logoutAccessManager.needsRuntimeTeardown(9L)).thenReturn(true);

        PlayerLogoutScRsp rsp = PlayerLogoutScRsp.parseFrom(
                service.handleLogout(
                        PlayerLogoutCsReq.newBuilder().setReason(0).build(),
                        1001L, 9L).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        verify(sceneActorService).onPlayerLeave(9L);
        verify(timerService).stopPlayerTimer(9L);
        verify(sessionService).markOffline(9L);
        verify(sessionService).clearAccountPlayer(1001L);
        verify(authTokenService).revokeTokenForAccount(1001L);
        verify(eventPublisher).publishPlayerLogout(eq(1001L), eq(9L), eq(0));
    }

    @Test
    public void logout_accountOnly_skipsSceneTeardown() throws Exception {
        when(logoutAccessManager.validateAccountSession(1001L)).thenReturn(RetCode.OK);
        when(logoutAccessManager.needsRuntimeTeardown(null)).thenReturn(false);

        PlayerLogoutScRsp rsp = PlayerLogoutScRsp.parseFrom(
                service.handleLogout(
                        PlayerLogoutCsReq.newBuilder().setReason(1).build(),
                        1001L, null).payload());

        assertThat(rsp.getRetcode()).isEqualTo(RetCode.OK);
        verify(sceneActorService, never()).onPlayerLeave(anyLong());
        verify(timerService, never()).stopPlayerTimer(anyLong());
        verify(sessionService).clearAccountPlayer(1001L);
        verify(authTokenService).revokeTokenForAccount(1001L);
    }

    private static Account account(long id, String name) {
        Account a = new Account();
        a.setId(id);
        a.setAccountName(name);
        return a;
    }

    private static Player player(long id, long accountId, String name, int level) {
        Player p = new Player();
        p.setId(id);
        p.setAccountId(accountId);
        p.setName(name);
        p.setLevel(level);
        return p;
    }
}
