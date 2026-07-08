/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AccountCredentialManager.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：账号凭证校验，规范化账号名、查 player_account 表、BCrypt 密码比对。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // player-service 登录验密环节，不签发 Token

import cn.itcast.demo.mymmorpg.entity.Account; // player_account 表 JPA 实体，含 password 哈希
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 登录失败时返回 ACCOUNT_NOT_FOUND / PASSWORD_WRONG
import cn.itcast.demo.mymmorpg.repository.AccountRepository; // 按 accountName 查 player_account 唯一行
import org.springframework.security.crypto.password.PasswordEncoder; // BCrypt 比对明文密码与库中 password 字段
import org.springframework.stereotype.Service; // AccountPlayerService 登录编排中密码校验步骤调用本类

/**
 * 账户凭证：规范化账号名、查 player_account、密码比对，不签发 Token 也不做准入策略。
 */
@Service // 登录编排层 AccountPlayerService 在密码校验前调用本类
public class AccountCredentialManager { // 仅负责 player_account 存在性与 BCrypt 验密

    /** player_account 表仓储，按规范化后的 accountName 唯一查询 */
    private final AccountRepository accountRepository; // findByAccountName → player_account 主键与 password 哈希

    /** Spring Security 配置的 BCrypt 编码器，matches 方法比对明文与哈希 */
    private final PasswordEncoder passwordEncoder; // 比对 AccountLoginCsReq.password 与 player_account.password

    /**
     * 构造器：AccountRepository 查 player_account，PasswordEncoder 验 BCrypt 密码。
     */
    public AccountCredentialManager(AccountRepository accountRepository, PasswordEncoder passwordEncoder) { // 构造器：AccountRepository 查 player_account，PasswordEncoder 验 BCrypt 密码
        this.accountRepository = accountRepository; // 按 accountName SELECT player_account
        this.passwordEncoder = passwordEncoder; // BCrypt.matches 验证明文与 password 哈希
    }

    /**
     * 将客户端原始账号名 trim，避免首尾空格导致 player_account 查无记录。
     *
     * @param raw 协议 AccountLoginCsReq.accountName 原始值
     * @return 去首尾空白后的账号名，null 视为空串
     */
    public static String normalizeAccountName(String raw) { // 将客户端原始账号名 trim，避免首尾空格导致 player_account 查无记录
        return raw == null ? "" : raw.trim(); // null 安全 trim，避免 " admin " 查不到 player_account.account_name
    }

    /**
     * 对已规范化账号名执行 player_account 查询 + BCrypt 密码校验。
     *
     * @param normalizedAccountName {@link #normalizeAccountName(String)} 的结果
     * @param rawPassword           客户端提交的明文密码
     * @return ok 含 Account 实体；deny 含 RetCode 拒绝码
     */
    public AccountCredentialOutcome verifyNormalizedName(String normalizedAccountName, String rawPassword) { // 对已规范化账号名执行 player_account 查询 + BCrypt 密码校验
        var opt = accountRepository.findByAccountName(normalizedAccountName); // SELECT player_account WHERE account_name=?
        if (opt.isEmpty()) { // account_name 在 player_account 无记录
            return AccountCredentialOutcome.deny(RetCode.ACCOUNT_NOT_FOUND); // AccountLoginScRsp retCode=ACCOUNT_NOT_FOUND
        }
        Account account = opt.get(); // 取出 player_account 行，含 id 与 password 哈希
        if (!passwordEncoder.matches(rawPassword, account.getPassword())) { // 明文与 BCrypt 哈希不匹配
            return AccountCredentialOutcome.deny(RetCode.PASSWORD_WRONG); // AccountLoginScRsp retCode=PASSWORD_WRONG
        }
        return AccountCredentialOutcome.ok(account); // 凭证有效，交由 AccountPlayerService 签发 auth:token
    }
}
