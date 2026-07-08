/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/LoginAdmissionManager.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：登录准入检查，Groovy 脚本策略与 Redis 在线账号上限，不涉及密码校验。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // player-service 登录准入门控，先于 player_account 验密

import cn.itcast.demo.mymmorpg.support.LoginPolicy; // Groovy 脚本：按账号名决定是否允许登录
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 准入拒绝时返回 INTERNAL_ERROR / SERVER_OVERLOADED
import cn.itcast.demo.mymmorpg.service.AuthTokenService; // 统计 auth:account:* 键数量判断服务器是否满载
import org.springframework.stereotype.Service; // AccountPlayerService.handleAccountLogin 在验密前调用

/**
 * 登录准入：脚本策略、认证容量等，与具体账号密码无关。
 */
@Service // 登录流程第一步：准入通过后才 SELECT player_account 并 BCrypt 验密
public class LoginAdmissionManager { // Groovy allowLogin + auth:account:* 在线上限，不涉及密码

    /** Groovy 登录策略，可按账号名/维护窗口等拒绝登录 */
    private final LoginPolicy loginPolicy; // allowLogin(normalizedAccountName) 维护窗口/黑名单判定

    /** 单点登录 Token 服务，提供 Redis 在线账号计数与上限判断 */
    private final AuthTokenService authTokenService; // countOnlineAccounts 扫描 auth:account:* 键数量

    public LoginAdmissionManager(LoginPolicy loginPolicy, AuthTokenService authTokenService) { // 登录准入：脚本策略、认证容量等，与具体账号密码无关
        this.loginPolicy = loginPolicy; // Groovy allowLogin 脚本，拒绝时返回 RetCode.INTERNAL_ERROR
        this.authTokenService = authTokenService; // isServerOverloaded 对比 game.auth.max-online-accounts
    }

    /**
     * 密码校验前的准入检查：脚本策略 + 服务器容量。
     *
     * @param normalizedAccountName 已规范化账号名
     * @return {@link RetCode#OK} 表示可继续校验凭证，否则为拒绝原因码
     */
    public int checkBeforeCredential(String normalizedAccountName) { // 密码校验前的准入检查：脚本策略 + 服务器容量
        if (!loginPolicy.allowLogin(normalizedAccountName)) { // Groovy 脚本拒绝该账号（维护/黑名单）
            return RetCode.INTERNAL_ERROR; // AccountLoginScRsp retCode=INTERNAL_ERROR，不查 player_account
        }
        if (authTokenService.isServerOverloaded()) { // auth:account:* 键数 ≥ game.auth.max-online-accounts
            return RetCode.SERVER_OVERLOADED; // AccountLoginScRsp retCode=SERVER_OVERLOADED，拒绝新登录
        }
        return RetCode.OK; // 准入通过，AccountPlayerService 可继续 verifyNormalizedName 验密
    }
}
