package cn.itcast.demo.mymmorpg.handler;

import cn.itcast.demo.mymmorpg.protocol.MessageId;

/**
 * 协议层会话鉴权：除账号登录外，其余消息须已绑定 accountId。
 */
public final class SessionAuthGuard {

    private SessionAuthGuard() {
    }

    public static boolean isPublicMessage(int msgId) {
        return msgId == MessageId.ACCOUNT_LOGIN_CS_REQ
                || msgId == MessageId.GET_VERSION_MANIFEST_CS_REQ
                || msgId == MessageId.VERIFY_RESOURCE_CHECKSUM_CS_REQ;
    }

    public static boolean isAuthenticated(DispatchSession session) {
        return session.accountId() != null && session.accountId() > 0;
    }
}
