/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AccountPlayerService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：账户登录、选角、登出编排，协调凭证/准入/Token/会话/事件/协议组包。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 账号登录/选角/登出编排，协调 Token/会话/事件/协议组包

import cn.itcast.demo.mymmorpg.entity.Account; // player_account 表实体
import cn.itcast.demo.mymmorpg.entity.Player; // player 表实体，选角后进游戏
import cn.itcast.demo.mymmorpg.event.PlayerDataPreloadEvent; // 选角成功后触发背包/活动等异步预加载
import cn.itcast.demo.mymmorpg.event.PlayerLoginEvent; // 选角进游戏后发布，触发功能解锁等
import cn.itcast.demo.mymmorpg.protocol.MessageId; // ACCOUNT_LOGIN/SELECT_PLAYER/PLAYER_LOGOUT 响应 msgId
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一 msgId + payload 响应封装
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 登录/选角/登出失败返回码
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq; // 账号登录请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp; // 账号登录响应，含 token 与角色列表
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerInfo; // 选角成功返回的完整角色信息
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutCsReq; // 登出请求，含 reason
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerLogoutScRsp; // 登出响应
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerProfile; // 登录响应中的角色摘要（id/name/level）
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerCsReq; // 选角请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.SelectPlayerScRsp; // 选角响应
import cn.itcast.demo.mymmorpg.repository.AccountRepository; // 更新 lastLoginTime
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // 按 accountId 查角色列表
import cn.itcast.demo.mymmorpg.service.AccountCredentialManager; // 账号名规范化与密码校验
import cn.itcast.demo.mymmorpg.service.LoginAdmissionManager; // 登录准入（脚本策略+在线上限）
import cn.itcast.demo.mymmorpg.service.PlayerLogoutAccessManager; // 登出会话校验与运行时拆除判断
import cn.itcast.demo.mymmorpg.service.PlayerSelectionAccessManager; // 选角归属校验与查 player 记录
import jforgame.commons.eventbus.EventBus; // 发布 PlayerLoginEvent / PlayerDataPreloadEvent
import org.springframework.stereotype.Service; // AccountFacade 注入本编排服务
import org.springframework.transaction.annotation.Transactional; // 登录/选角涉及 DB 写操作

import java.time.LocalDateTime; // 记录账号最后登录时间
import java.time.ZoneId; // 服务端时区，供 serverTimeMillis 使用

/**
 * 账户登录、选角、登出编排：按步骤调用各访问/凭证管理类，协议组包与事件发布在本类完成。
 */
@Service // 账号生命周期核心编排层
public class AccountPlayerService { // handleAccountLogin/handleSelectPlayer/handleLogout 三协议入口

    private final AccountRepository accountRepository; // 更新 player_account.last_login_time
    private final PlayerRepository playerRepository; // 查账号下全部角色列表
    private final PlayerSessionService sessionService; // Redis 标记 player 在线/离线
    private final PlayerEventPublisher eventPublisher; // MQ/NoOp 发布账号登录/进游戏/登出领域事件
    private final SceneActorService sceneActorService; // 登出时通知场景移除 Actor
    private final PlayerTimerPersistenceService playerTimerPersistenceService; // 选角启动/登出停止定时落库
    private final EventBus eventBus; // 进程内发布 PlayerLoginEvent / PlayerDataPreloadEvent
    private final AuthTokenService authTokenService; // 登录成功签发 Redis 单点登录 Token
    private final LoginAdmissionManager loginAdmissionManager; // 密码校验前的 Groovy 策略与在线上限
    private final AccountCredentialManager accountCredentialManager; // 查 player_account 并 BCrypt 验密
    private final PlayerSelectionAccessManager playerSelectionAccessManager; // 选角归属与会话 accountId 校验
    private final PlayerLogoutAccessManager playerLogoutAccessManager; // 登出会话有效性校验

    public AccountPlayerService( // 账户登录、选角、登出编排：按步骤调用各访问/凭证管理类，协议组包与事件发布在本类完成
            AccountRepository accountRepository, // 写 last_login_time 到 player_account
            PlayerRepository playerRepository, // 登录响应组装角色 PlayerProfile 列表
            PlayerSessionService sessionService, // 选角 markOnline、登出 markOffline
            PlayerEventPublisher eventPublisher, // 账号登录/进游戏/登出 MQ 或日志事件
            SceneActorService sceneActorService, // 登出 onPlayerLeave 移除场景实体
            PlayerTimerPersistenceService playerTimerPersistenceService, // 选角 startTimer、登出 stopTimer+flush
            EventBus eventBus, // 选角后 PlayerLoginEvent 与 PlayerDataPreloadEvent
            AuthTokenService authTokenService, // 签发 auth:token:{token} 并踢旧登录
            LoginAdmissionManager loginAdmissionManager, // 验密前准入：脚本策略+服务器满载
            AccountCredentialManager accountCredentialManager, // 规范化账号名并验 BCrypt 密码
            PlayerSelectionAccessManager playerSelectionAccessManager, // 会话 accountId 与选角归属
            PlayerLogoutAccessManager playerLogoutAccessManager) { // 登出会话校验与是否需拆运行时
        this.accountRepository = accountRepository; // 登录成功更新 last_login_time
        this.playerRepository = playerRepository; // 查 findByAccountIdOrderByIdAsc 组角色列表
        this.sessionService = sessionService; // Redis player:online:{playerId} 在线标记
        this.eventPublisher = eventPublisher; // publishAccountLogin/Enter/Logout 领域事件
        this.sceneActorService = sceneActorService; // 登出拆除场景 Actor
        this.playerTimerPersistenceService = playerTimerPersistenceService; // 脏数据周期 flush 定时器
        this.eventBus = eventBus; // 功能解锁与预加载 EventBus 事件
        this.authTokenService = authTokenService; // Redis 双向 Token 映射与单点登录
        this.loginAdmissionManager = loginAdmissionManager; // Groovy allowLogin + max-online-accounts
        this.accountCredentialManager = accountCredentialManager; // SELECT player_account 并 BCrypt 验密
        this.playerSelectionAccessManager = playerSelectionAccessManager; // 防跨号选角
        this.playerLogoutAccessManager = playerLogoutAccessManager; // NOT_LOGGED_IN 与运行时拆除门控
    }

    /** 账号登录：准入 → 验密 → 更新 lastLogin → 签发 Token → 返回角色列表 */
    @Transactional // 更新 account.last_login_time 需事务
    public ProtocolMessage handleAccountLogin(AccountLoginCsReq req) { // 账户登录、选角、登出编排：按步骤调用各访问/凭证管理类，协议组包与事件发布在本类完成
        String name = AccountCredentialManager.normalizeAccountName(req.getAccountName()); // trim 协议账号名
        int admission = loginAdmissionManager.checkBeforeCredential(name); // Groovy 策略 + auth:account:* 在线上限
        if (admission != RetCode.OK) { // 准入拒绝：维护窗口/黑名单/服务器满载
            return accountLoginError(admission); // 组 AccountLoginScRsp 含 SERVER_OVERLOADED 等 retCode
        }
        var credential = accountCredentialManager.verifyNormalizedName(name, req.getPassword()); // 查 player_account 验 BCrypt
        if (!credential.success()) { // 账号不存在或密码错误
            return accountLoginError(credential.retCode()); // ACCOUNT_NOT_FOUND 或 PASSWORD_WRONG
        }
        Account account = credential.account(); // 凭证通过，取出账号实体含 id
        persistAccountLastLogin(account); // UPDATE player_account.last_login_time=now
        String token = authTokenService.issueTokenForAccount(account.getId()); // 签发 auth:token 并踢旧 Token
        ProtocolMessage response = buildAccountLoginSuccess(account, token); // 组 token + PlayerProfile 列表
        eventPublisher.publishAccountLogin(account.getId(), account.getAccountName()); // MQ/日志：账号登录事件
        return response; // 下发 AccountLoginScRsp 给客户端
    }

    /** 选角进游戏：会话绑定校验 → 查角色 → 标记在线 → 启动定时器 → 发布事件 */
    @Transactional(readOnly = true) // 选角 mostly 读，markOnline 写 Redis
    public ProtocolMessage handleSelectPlayer(SelectPlayerCsReq req, long boundAccountId, String sessionMarker) { // 账户登录、选角、登出编排：按步骤调用各访问/凭证管理类，协议组包与事件发布在本类完成
        int binding = playerSelectionAccessManager.validateSessionMatchesRequest(req.getAccountId(), boundAccountId); // Token 绑定 accountId 与请求一致
        if (binding != RetCode.OK) { // 会话未登录或 accountId 被篡改
            return selectPlayerError(binding); // NOT_LOGGED_IN
        }
        var opt = playerSelectionAccessManager.findPlayerOwnedByAccount(req.getPlayerId(), req.getAccountId()); // 查 player 是否属于该账号
        if (opt.isEmpty()) { // playerId 不存在或不属于 accountId
            return selectPlayerError(RetCode.PLAYER_NOT_FOUND); // 拒绝选角
        }
        Player p = opt.get(); // 取出选中角色实体
        markPlayerSessionOnline(p, sessionMarker); // Redis/内存标记 player 在线，绑定 sessionMarker
        startPlayerPersistenceTimer(p.getId()); // 启动周期 flush 脏 Player 缓存
        notifyPlayerEnteredGame(req.getAccountId(), p); // MQ：PlayerEnter 事件（场景/推送）
        publishPlayerLoginEvent(p); // EventBus：PlayerLoginEvent 触发功能解锁
        publishPlayerDataPreloadEvent(p.getId()); // EventBus：背包/活动异步预加载
        return selectPlayerSuccess(p); // 下发 SelectPlayerScRsp 含 PlayerInfo 与 serverTime
    }

    /** 登出：会话校验 → 拆除运行时（场景/定时器/在线标记）→ 发布登出事件 */
    public ProtocolMessage handleLogout(PlayerLogoutCsReq req, long boundAccountId, Long boundPlayerId) { // 登出：会话校验 → 拆除运行时（场景/定时器/在线标记）→ 发布登出事件
        int sessionOk = playerLogoutAccessManager.validateAccountSession(boundAccountId); // 连接是否仍绑定有效 accountId
        if (sessionOk != RetCode.OK) { // 未登录或会话已失效
            return playerLogoutError(sessionOk); // NOT_LOGGED_IN
        }
        tearDownBoundPlayerRuntime(boundPlayerId); // 已选角则清场景/定时器/在线态
        eventPublisher.publishPlayerLogout(boundAccountId, boundPlayerId, req.getReason()); // MQ：登出事件含 reason
        return playerLogoutSuccess(); // 下发 PlayerLogoutScRsp retCode=OK
    }

    /** 供 WebSocket 等展示用服务器时间（JVM 默认时区） */
    public static long serverTimeMillis() { // 供 WebSocket 等展示用服务器时间（JVM 默认时区）
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); // 当前时区毫秒时间戳供客户端校时
    }

    /** Player 实体 → 登录响应中的 PlayerProfile 摘要 */
    static PlayerProfile toPlayerProfile(Player p) { // Player 实体 → 登录响应中的 PlayerProfile 摘要
        return PlayerProfile.newBuilder() // 登录响应角色摘要 Protobuf 构建器
                .setPlayerId(p.getId()) // 角色主键 player.id
                .setPlayerName(p.getName()) // 角色显示名
                .setLevel(p.getLevel() == null ? 1 : p.getLevel()) // 等级，null 默认 1 级
                .build(); // 完成 PlayerProfile Protobuf 构建
    }

    /** Player 实体 → 选角成功响应中的 PlayerInfo 完整信息 */
    static PlayerInfo toPlayerInfo(Player p, long serverTimeMillis) { // Player 实体 → 选角成功响应中的 PlayerInfo 完整信息
        return PlayerInfo.newBuilder() // 选角成功 PlayerInfo Protobuf 构建器
                .setPlayerId(p.getId()) // 选角后客户端缓存的角色 ID
                .setPlayerName(p.getName()) // 角色名展示
                .setLevel(p.getLevel() == null ? 1 : p.getLevel()) // 当前等级
                .setVipRight(p.getVipRight() == null ? 0 : p.getVipRight()) // VIP 权益位图
                .setServerTime(serverTimeMillis) // 服务端时间戳供校时
                .build(); // 完成 PlayerInfo Protobuf 构建
    }

    /** 更新 player_account.last_login_time 并 save */
    private void persistAccountLastLogin(Account account) { // 更新 player_account.last_login_time 并 save
        account.setLastLoginTime(LocalDateTime.now()); // 记录本次登录时间
        accountRepository.save(account); // UPDATE player_account 行
    }

    /** 构造 AccountLoginScRsp 失败响应 */
    private ProtocolMessage accountLoginError(int retCode) { // 构造 AccountLoginScRsp 失败响应
        return new ProtocolMessage(MessageId.ACCOUNT_LOGIN_SC_RSP, // msgId=ACCOUNT_LOGIN_SC_RSP
                AccountLoginScRsp.newBuilder()
                        .setRetcode(retCode)
                        .setAccountId(0)
                        .build().toByteArray()); // retCode 非 OK，accountId=0
    }

    /** 构造 AccountLoginScRsp 成功响应：token + 账号下全部角色 PlayerProfile 列表 */
    private ProtocolMessage buildAccountLoginSuccess(Account account, String token) { // 构造 AccountLoginScRsp 成功响应：token + 账号下全部角色 PlayerProfile 列表
        var b = AccountLoginScRsp.newBuilder() // 登录成功响应 Protobuf 构建器
                .setRetcode(RetCode.OK) // 登录成功
                .setAccountId(account.getId()) // 客户端缓存 accountId
                .setToken(token); // 后续请求/WebSocket 鉴权携带
        for (var p : playerRepository.findByAccountIdOrderByIdAsc(account.getId())) { // 按 id 升序查该账号全部角色
            b.addPlayerList(toPlayerProfile(p)); // 追加角色摘要至列表
        }
        return new ProtocolMessage(MessageId.ACCOUNT_LOGIN_SC_RSP, b.build().toByteArray()); // msgId=ACCOUNT_LOGIN_SC_RSP
    }

    /** 构造 SelectPlayerScRsp 失败响应 */
    private ProtocolMessage selectPlayerError(int retCode) { // 构造 SelectPlayerScRsp 失败响应
        return new ProtocolMessage(MessageId.SELECT_PLAYER_SC_RSP, // msgId=SELECT_PLAYER_SC_RSP
                SelectPlayerScRsp.newBuilder()
                        .setRetcode(retCode)
                        .build().toByteArray()); // 仅 retCode，无 PlayerInfo
    }

    /** 构造 SelectPlayerScRsp 成功响应，含 PlayerInfo 与 serverTime */
    private ProtocolMessage selectPlayerSuccess(Player p) { // 构造 SelectPlayerScRsp 成功响应，含 PlayerInfo 与 serverTime
        PlayerInfo info = toPlayerInfo(p, System.currentTimeMillis()); // 嵌入当前服务端时间戳
        return new ProtocolMessage(MessageId.SELECT_PLAYER_SC_RSP, // msgId=SELECT_PLAYER_SC_RSP 选角成功
                SelectPlayerScRsp.newBuilder()
                        .setRetcode(RetCode.OK)
                        .setPlayerInfo(info)
                        .build().toByteArray()); // 选角成功完整响应
    }

    /** 标记 player 在线，sessionMarker 通常为 WebSocket sessionId */
    private void markPlayerSessionOnline(Player p, String sessionMarker) { // 标记 player 在线，sessionMarker 通常为 WebSocket sessionId
        String marker = sessionMarker != null ? sessionMarker : "unknown"; // 无 marker 时用占位符
        sessionService.markOnline(p.getId(), marker); // 写 Redis player:online:{playerId}
    }

    /** 选角成功后启动 player 定时持久化任务 */
    private void startPlayerPersistenceTimer(long playerId) { // 选角成功后启动 player 定时持久化任务
        playerTimerPersistenceService.startPlayerTimer(playerId); // 周期性 flush entity:player 脏数据至 MySQL
    }

    /** 发布玩家进游戏事件（场景/推送消费） */
    private void notifyPlayerEnteredGame(long accountId, Player p) { // 发布玩家进游戏事件（场景/推送消费）
        eventPublisher.publishPlayerEnter(accountId, p.getId(), p.getName()); // accountId + playerId + name
    }

    /** EventBus 发布 PlayerLoginEvent，FunctionFacade 订阅触发功能解锁 */
    private void publishPlayerLoginEvent(Player p) { // EventBus 发布 PlayerLoginEvent，FunctionFacade 订阅触发功能解锁
        eventBus.publish(new PlayerLoginEvent(p)); // 携带 Player 实体供 FunctionService.checkOpen
    }

    /** EventBus 发布 PlayerDataPreloadEvent，触发背包/活动异步预加载 */
    private void publishPlayerDataPreloadEvent(long playerId) { // EventBus 发布 PlayerDataPreloadEvent，触发背包/活动异步预加载
        eventBus.publish(new PlayerDataPreloadEvent(playerId)); // PlayerDataAsyncPreloadService 监听并 triggerAll
    }

    /** 构造 PlayerLogoutScRsp 失败响应 */
    private ProtocolMessage playerLogoutError(int retCode) { // 构造 PlayerLogoutScRsp 失败响应
        return new ProtocolMessage(MessageId.PLAYER_LOGOUT_SC_RSP, // msgId=PLAYER_LOGOUT_SC_RSP 失败
                PlayerLogoutScRsp.newBuilder()
                        .setRetcode(retCode)
                        .build().toByteArray()); // 登出失败 retCode
    }

    /** 构造 PlayerLogoutScRsp 成功响应 */
    private ProtocolMessage playerLogoutSuccess() { // 构造 PlayerLogoutScRsp 成功响应
        return new ProtocolMessage(MessageId.PLAYER_LOGOUT_SC_RSP, // msgId=PLAYER_LOGOUT_SC_RSP 成功
                PlayerLogoutScRsp.newBuilder()
                        .setRetcode(RetCode.OK)
                        .build().toByteArray()); // 登出成功
    }

    /** 登出时拆除已选角 player 的运行时状态：场景离开、停定时器、标记离线 */
    private void tearDownBoundPlayerRuntime(Long boundPlayerId) { // 登出时拆除已选角 player 的运行时状态：场景离开、停定时器、标记离线
        if (!playerLogoutAccessManager.needsRuntimeTeardown(boundPlayerId)) { // 仅登录未选角，boundPlayerId 为 null/0
            return; // 无需清场景/定时器/在线标记
        }
        notifyPlayerLeftScene(boundPlayerId); // 场景内移除该 player 的 Actor
        stopPlayerPersistenceTimer(boundPlayerId); // 停止定时落库并最后一次 flush
        markPlayerSessionOffline(boundPlayerId); // 清除 Redis 在线标记
    }

    /** 通知场景服务玩家离开 */
    private void notifyPlayerLeftScene(long playerId) { // 通知场景服务玩家离开
        sceneActorService.onPlayerLeave(playerId); // 场景内移除该 player 的 Actor
    }

    /** 停止 player 定时持久化 */
    private void stopPlayerPersistenceTimer(long playerId) { // 停止 player 定时持久化
        playerTimerPersistenceService.stopPlayerTimer(playerId); // cancel scheduled task 并 flush 脏数据
    }

    /** 标记 player 离线 */
    private void markPlayerSessionOffline(long playerId) { // 标记 player 离线
        sessionService.markOffline(playerId); // 删除 Redis player:online:{playerId}
    }
}
