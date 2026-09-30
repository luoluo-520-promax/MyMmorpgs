/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AccountPlayerService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：账户登录、选角（SELECT_ROLE）、登出编排；多端策略/风控/封禁接入。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Account;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.anticheat.ClientIntegrityChecker;
import cn.itcast.demo.mymmorpg.event.PlayerDataPreloadEvent;
import cn.itcast.demo.mymmorpg.event.PlayerLoginEvent;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerProfile;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RenewTicketCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.RenewTicketScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerScRsp;
import cn.itcast.demo.mymmorpg.repository.AccountRepository;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import jforgame.commons.eventbus.EventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * 账户登录、选角、登出编排。选角协议即 SELECT_ROLE：登录后返回角色列表，客户端再选角色进入。
 */
@Service
public class AccountPlayerService {

    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;
    private final PlayerSessionService sessionService;
    private final PlayerEventPublisher eventPublisher;
    private final SceneActorService sceneActorService;
    private final PlayerTimerPersistenceService playerTimerPersistenceService;
    private final EventBus eventBus;
    private final AuthTokenService authTokenService;
    private final LoginAdmissionManager loginAdmissionManager;
    private final AccountCredentialManager accountCredentialManager;
    private final PlayerSelectionAccessManager playerSelectionAccessManager;
    private final PlayerLogoutAccessManager playerLogoutAccessManager;
    private final SessionKickService sessionKickService;
    private final ObjectProvider<LoginRateLimiter> loginRateLimiter;
    private final ObjectProvider<LoginHistoryService> loginHistoryService;
    private final ObjectProvider<BanService> banService;
    private final ObjectProvider<ClientIntegrityChecker> clientIntegrityChecker;
    private final ClientVersionGate clientVersionGate;
    private final String defaultNodeId;

    public AccountPlayerService(
            AccountRepository accountRepository,
            PlayerRepository playerRepository,
            PlayerSessionService sessionService,
            PlayerEventPublisher eventPublisher,
            SceneActorService sceneActorService,
            PlayerTimerPersistenceService playerTimerPersistenceService,
            EventBus eventBus,
            AuthTokenService authTokenService,
            LoginAdmissionManager loginAdmissionManager,
            AccountCredentialManager accountCredentialManager,
            PlayerSelectionAccessManager playerSelectionAccessManager,
            PlayerLogoutAccessManager playerLogoutAccessManager,
            SessionKickService sessionKickService,
            ObjectProvider<LoginRateLimiter> loginRateLimiter,
            ObjectProvider<LoginHistoryService> loginHistoryService,
            ObjectProvider<BanService> banService,
            ObjectProvider<ClientIntegrityChecker> clientIntegrityChecker,
            ClientVersionGate clientVersionGate,
            @Value("${game.center.local-node-id:local}") String defaultNodeId) {
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.sceneActorService = sceneActorService;
        this.playerTimerPersistenceService = playerTimerPersistenceService;
        this.eventBus = eventBus;
        this.authTokenService = authTokenService;
        this.loginAdmissionManager = loginAdmissionManager;
        this.accountCredentialManager = accountCredentialManager;
        this.playerSelectionAccessManager = playerSelectionAccessManager;
        this.playerLogoutAccessManager = playerLogoutAccessManager;
        this.sessionKickService = sessionKickService;
        this.loginRateLimiter = loginRateLimiter;
        this.loginHistoryService = loginHistoryService;
        this.banService = banService;
        this.clientIntegrityChecker = clientIntegrityChecker;
        this.clientVersionGate = clientVersionGate == null ? new ClientVersionGate() : clientVersionGate;
        this.defaultNodeId = defaultNodeId == null || defaultNodeId.isBlank() ? "local" : defaultNodeId;
    }

    /** 兼容旧单测构造（无风控 Bean）。 */
    public AccountPlayerService(
            AccountRepository accountRepository,
            PlayerRepository playerRepository,
            PlayerSessionService sessionService,
            PlayerEventPublisher eventPublisher,
            SceneActorService sceneActorService,
            PlayerTimerPersistenceService playerTimerPersistenceService,
            EventBus eventBus,
            AuthTokenService authTokenService,
            LoginAdmissionManager loginAdmissionManager,
            AccountCredentialManager accountCredentialManager,
            PlayerSelectionAccessManager playerSelectionAccessManager,
            PlayerLogoutAccessManager playerLogoutAccessManager,
            SessionKickService sessionKickService) {
        this(accountRepository, playerRepository, sessionService, eventPublisher, sceneActorService,
                playerTimerPersistenceService, eventBus, authTokenService, loginAdmissionManager,
                accountCredentialManager, playerSelectionAccessManager, playerLogoutAccessManager,
                sessionKickService, new EmptyProvider<>(), new EmptyProvider<>(), new EmptyProvider<>(),
                new EmptyProvider<>(), new ClientVersionGate(), "local");
    }

    @Transactional
    public ProtocolMessage handleAccountLogin(AccountLoginCsReq req) {
        String name = AccountCredentialManager.normalizeAccountName(req.getAccountName());
        AuthTokenService.LoginDeviceContext device = new AuthTokenService.LoginDeviceContext(
                req.getDeviceId(), req.getClientType(), req.getClientIp(), req.getUserAgent());

        LoginRateLimiter limiter = loginRateLimiter == null ? null : loginRateLimiter.getIfAvailable();
        if (limiter != null && limiter.isLimited(device.clientIp(), name)) {
            return accountLoginError(RetCode.LOGIN_RATE_LIMITED);
        }

        int admission = loginAdmissionManager.checkBeforeCredential(name);
        if (admission != RetCode.OK) {
            return accountLoginError(admission);
        }
        var credential = accountCredentialManager.verifyNormalizedName(name, req.getPassword());
        if (!credential.success()) {
            recordHistory(0L, name, device, false, "AUTH_FAIL");
            return accountLoginError(credential.retCode());
        }
        Account account = credential.account();

        BanService bans = banService == null ? null : banService.getIfAvailable();
        if (bans != null) {
            int banCode = bans.checkAccount(account);
            if (banCode != RetCode.OK) {
                recordHistory(account.getId(), name, device, false, "ACCOUNT_BANNED");
                return accountLoginError(banCode);
            }
        }

        ClientIntegrityChecker.IntegrityResult integrity = evaluateClientIntegrity(req);
        if (!integrity.trusted()) {
            recordHistory(account.getId(), name, device, false, "CLIENT_UNTRUSTED:" + integrity.reasons());
            return accountLoginError(RetCode.CLIENT_UNTRUSTED);
        }

        ClientVersionGate.GateResult versionGate = clientVersionGate.evaluate(
                req.getClientVersionNumber(),
                req.getProtocolSchemaHash(),
                account.getId());
        if (versionGate.retCode() != RetCode.OK) {
            recordHistory(account.getId(), name, device, false, "CLIENT_VERSION_TOO_OLD:" + req.getClientVersionNumber());
            return accountLoginError(versionGate.retCode());
        }

        LoginHistoryService history = loginHistoryService == null ? null : loginHistoryService.getIfAvailable();
        String risk = history == null ? null : history.evaluateRisk(account.getId(), device);
        boolean needSecondAuth = risk != null && (risk.contains("NEW_IP") || risk.contains("NEW_DEVICE"));
        // 模拟器等软标志：允许登录但要求二次验证
        if (integrity.score() < 100 && !integrity.reasons().isEmpty()) {
            needSecondAuth = true;
        }
        // 当前版本：标记 need_second_auth，仍允许登录（二次验证通道可后续接邮箱码）
        if (needSecondAuth && history != null) {
            // 仅记录风险，不阻断；若产品要求强拦可改为 RetCode.NEED_SECOND_AUTH
        }

        persistAccountLastLogin(account);
        sessionKickService.kickPreviousSession(account.getId(), device.deviceId());
        AuthTokenService.IssueResult issued = authTokenService.issueToken(account.getId(), device);
        if (limiter != null) {
            limiter.clearOnSuccess(device.clientIp(), name);
        }
        recordHistory(account.getId(), name, device, true, risk);
        ProtocolMessage response = buildAccountLoginSuccess(
                account, issued.token(), issued.expireAtMillis(), needSecondAuth, versionGate.recommendUpdate());
        eventPublisher.publishAccountLogin(account.getId(), account.getAccountName());
        return response;
    }

    /** SELECT_ROLE：登录后选择角色进入游戏。 */
    @Transactional(readOnly = true)
    public ProtocolMessage handleSelectPlayer(SelectPlayerCsReq req, long boundAccountId, String sessionMarker) {
        int binding = playerSelectionAccessManager.validateSessionMatchesRequest(req.getAccountId(), boundAccountId);
        if (binding != RetCode.OK) {
            return selectPlayerError(binding);
        }
        var opt = playerSelectionAccessManager.findPlayerOwnedByAccount(req.getPlayerId(), req.getAccountId());
        if (opt.isEmpty()) {
            return selectPlayerError(RetCode.PLAYER_NOT_FOUND);
        }
        Player p = opt.get();
        BanService bans = banService == null ? null : banService.getIfAvailable();
        if (bans != null) {
            int banCode = bans.checkPlayer(p);
            if (banCode != RetCode.OK) {
                return selectPlayerError(banCode);
            }
        }
        markPlayerSessionOnline(p, sessionMarker);
        startPlayerPersistenceTimer(p.getId());
        notifyPlayerEnteredGame(req.getAccountId(), p);
        publishPlayerLoginEvent(p);
        publishPlayerDataPreloadEvent(p.getId());
        return selectPlayerSuccess(p);
    }

    public ProtocolMessage handleLogout(PlayerLogoutCsReq req, long boundAccountId, Long boundPlayerId) {
        int sessionOk = playerLogoutAccessManager.validateAccountSession(boundAccountId);
        if (sessionOk != RetCode.OK) {
            return playerLogoutError(sessionOk);
        }
        tearDownBoundPlayerRuntime(boundPlayerId);
        sessionService.clearAccountPlayer(boundAccountId);
        authTokenService.revokeTokenForAccount(boundAccountId);
        eventPublisher.publishPlayerLogout(boundAccountId, boundPlayerId, req.getReason());
        return playerLogoutSuccess();
    }

    /** 票据刷新：TTL 过半时客户端调用，延长登录态。 */
    public ProtocolMessage handleRenewTicket(RenewTicketCsReq req) {
        AuthTokenService.IssueResult renewed = authTokenService.renewToken(req.getToken(), req.getDeviceId());
        if (renewed == null) {
            // 降级：若 token 失效但能解析出账号且会话仍在，允许按 device 重签
            AuthTokenService.TokenMeta meta = authTokenService.getTokenMeta(req.getToken());
            if (meta != null) {
                Long bound = sessionService.findBoundPlayerId(meta.accountId());
                if (bound != null && sessionService.isOnline(bound)) {
                    renewed = authTokenService.reissueByAccount(meta.accountId(),
                            new AuthTokenService.LoginDeviceContext(req.getDeviceId(), meta.clientType(),
                                    meta.clientIp(), meta.userAgent()));
                }
            }
        }
        if (renewed == null) {
            return new ProtocolMessage(MessageId.RENEW_TICKET_SC_RSP,
                    RenewTicketScRsp.newBuilder().setRetcode(RetCode.NOT_LOGGED_IN).build().toByteArray());
        }
        return new ProtocolMessage(MessageId.RENEW_TICKET_SC_RSP,
                RenewTicketScRsp.newBuilder()
                        .setRetcode(RetCode.OK)
                        .setToken(renewed.token())
                        .setTokenExpireAt(renewed.expireAtMillis())
                        .build().toByteArray());
    }

    public static long serverTimeMillis() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static PlayerProfile toPlayerProfile(Player p) {
        return PlayerProfile.newBuilder()
                .setPlayerId(p.getId())
                .setPlayerName(p.getName())
                .setLevel(p.getLevel() == null ? 1 : p.getLevel())
                .build();
    }

    static PlayerInfo toPlayerInfo(Player p, long serverTimeMillis) {
        return PlayerInfo.newBuilder()
                .setPlayerId(p.getId())
                .setPlayerName(p.getName())
                .setLevel(p.getLevel() == null ? 1 : p.getLevel())
                .setVipRight(p.getVipRight() == null ? 0 : p.getVipRight())
                .setServerTime(serverTimeMillis)
                .build();
    }

    private void persistAccountLastLogin(Account account) {
        account.setLastLoginTime(LocalDateTime.now());
        accountRepository.save(account);
    }

    private ProtocolMessage accountLoginError(int retCode) {
        return new ProtocolMessage(MessageId.ACCOUNT_LOGIN_SC_RSP,
                AccountLoginScRsp.newBuilder()
                        .setRetcode(retCode)
                        .setAccountId(0)
                        .build().toByteArray());
    }

    private ProtocolMessage buildAccountLoginSuccess(Account account, String token) {
        return buildAccountLoginSuccess(account, token, authTokenService.tokenExpireAtMillis(), false, false);
    }

    private ProtocolMessage buildAccountLoginSuccess(Account account, String token, long expireAt, boolean needSecondAuth) {
        return buildAccountLoginSuccess(account, token, expireAt, needSecondAuth, false);
    }

    private ProtocolMessage buildAccountLoginSuccess(
            Account account, String token, long expireAt, boolean needSecondAuth, boolean recommendClientUpdate) {
        var b = AccountLoginScRsp.newBuilder()
                .setRetcode(RetCode.OK)
                .setAccountId(account.getId())
                .setToken(token)
                .setTokenExpireAt(expireAt)
                .setNeedSecondAuth(needSecondAuth)
                .setRecommendClientUpdate(recommendClientUpdate);
        for (var p : playerRepository.findByAccountIdOrderByIdAsc(account.getId())) {
            b.addPlayerList(toPlayerProfile(p));
        }
        return new ProtocolMessage(MessageId.ACCOUNT_LOGIN_SC_RSP, b.build().toByteArray());
    }

    private ProtocolMessage selectPlayerError(int retCode) {
        return new ProtocolMessage(MessageId.SELECT_PLAYER_SC_RSP,
                SelectPlayerScRsp.newBuilder().setRetcode(retCode).build().toByteArray());
    }

    private ProtocolMessage selectPlayerSuccess(Player p) {
        PlayerInfo info = toPlayerInfo(p, System.currentTimeMillis());
        return new ProtocolMessage(MessageId.SELECT_PLAYER_SC_RSP,
                SelectPlayerScRsp.newBuilder().setRetcode(RetCode.OK).setPlayerInfo(info).build().toByteArray());
    }

    private void markPlayerSessionOnline(Player p, String sessionMarker) {
        String marker = sessionMarker != null ? sessionMarker : "unknown";
        sessionService.markOnline(p.getId(), PlayerSessionService.OnlinePresence.of(
                marker, defaultNodeId, 0, null, null, null));
        if (p.getAccountId() != null) {
            sessionService.bindAccountPlayer(p.getAccountId(), p.getId());
        }
    }

    private void startPlayerPersistenceTimer(long playerId) {
        playerTimerPersistenceService.startPlayerTimer(playerId);
    }

    private void notifyPlayerEnteredGame(long accountId, Player p) {
        eventPublisher.publishPlayerEnter(accountId, p.getId(), p.getName());
    }

    private void publishPlayerLoginEvent(Player p) {
        eventBus.publish(new PlayerLoginEvent(p));
    }

    private void publishPlayerDataPreloadEvent(long playerId) {
        eventBus.publish(new PlayerDataPreloadEvent(playerId));
    }

    private ProtocolMessage playerLogoutError(int retCode) {
        return new ProtocolMessage(MessageId.PLAYER_LOGOUT_SC_RSP,
                PlayerLogoutScRsp.newBuilder().setRetcode(retCode).build().toByteArray());
    }

    private ProtocolMessage playerLogoutSuccess() {
        return new ProtocolMessage(MessageId.PLAYER_LOGOUT_SC_RSP,
                PlayerLogoutScRsp.newBuilder().setRetcode(RetCode.OK).build().toByteArray());
    }

    private void tearDownBoundPlayerRuntime(Long boundPlayerId) {
        if (!playerLogoutAccessManager.needsRuntimeTeardown(boundPlayerId)) {
            return;
        }
        notifyPlayerLeftScene(boundPlayerId);
        stopPlayerPersistenceTimer(boundPlayerId);
        markPlayerSessionOffline(boundPlayerId);
    }

    private void notifyPlayerLeftScene(long playerId) {
        sceneActorService.onPlayerLeave(playerId);
    }

    private void stopPlayerPersistenceTimer(long playerId) {
        playerTimerPersistenceService.stopPlayerTimer(playerId);
    }

    private void markPlayerSessionOffline(long playerId) {
        sessionService.markOffline(playerId);
    }

    private void recordHistory(long accountId, String name, AuthTokenService.LoginDeviceContext device,
                               boolean success, String risk) {
        LoginHistoryService history = loginHistoryService == null ? null : loginHistoryService.getIfAvailable();
        if (history != null && accountId > 0) {
            history.record(accountId, name, device, success, risk);
        }
    }

    private ClientIntegrityChecker.IntegrityResult evaluateClientIntegrity(AccountLoginCsReq req) {
        ClientIntegrityChecker checker = clientIntegrityChecker == null
                ? null : clientIntegrityChecker.getIfAvailable();
        if (checker == null) {
            checker = new ClientIntegrityChecker();
        }
        String fingerprint = req.getDeviceFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            fingerprint = req.getDeviceId();
        }
        Map<String, Object> flags = new HashMap<>();
        if (req.getIntegrityFlagsCount() > 0) {
            flags.putAll(req.getIntegrityFlagsMap());
        }
        return checker.check(fingerprint, flags);
    }

    /** 单测用空 ObjectProvider。 */
    private static final class EmptyProvider<T> implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return null;
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            return null;
        }
    }
}
