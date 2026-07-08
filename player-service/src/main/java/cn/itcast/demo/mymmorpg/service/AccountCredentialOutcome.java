/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AccountCredentialOutcome.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：封装账号+密码校验结果，区分成功（含 Account）与拒绝（含 RetCode）。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // player-service 账号凭证校验结果值对象

import cn.itcast.demo.mymmorpg.entity.Account; // 校验成功时携带的 player_account 实体
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 校验失败时的协议返回码

/**
 * 账户名 + 密码校验结果值对象：成功时 account 非空，失败时 account 为 null 且 retCode 为拒绝码。
 */
public record AccountCredentialOutcome( // 账户名 + 密码校验结果值对象：成功时 account 非空，失败时 account 为 null 且 retCode 为拒绝码
        Account account, // 凭证通过时为 player_account 行，拒绝时为 null
        int retCode) { // RetCode.OK 或 ACCOUNT_NOT_FOUND / PASSWORD_WRONG

    /**
     * 构造校验成功结果，retCode 固定为 RetCode.OK。
     */
    public static AccountCredentialOutcome ok(Account account) { // 构造校验成功结果，retCode 固定为 RetCode.OK
        return new AccountCredentialOutcome(account, RetCode.OK); // 凭证通过，AccountPlayerService 可签发 auth:token 并组 AccountLoginScRsp
    }

    /**
     * 构造校验失败结果，account 置 null，retCode 为具体拒绝原因。
     */
    public static AccountCredentialOutcome deny(int retCode) { // 构造校验失败结果，account 置 null，retCode 为具体拒绝原因
        return new AccountCredentialOutcome(null, retCode); // 无账号实体，AccountPlayerService 直接写 retCode 进 AccountLoginScRsp
    }

    /**
     * 是否校验通过：account 非空即视为成功。
     */
    public boolean success() { // 是否校验通过：account 非空即视为成功
        return account != null; // player_account 存在且 BCrypt 密码匹配，可继续 UPDATE last_login_time 与 Token 签发
    }
}
