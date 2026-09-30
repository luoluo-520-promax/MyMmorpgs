/**
 * 社交领域事件发布：好友上线、组队、助战结算、家园拜访等，供活动/任务/成就订阅。
 */
package cn.itcast.demo.mymmorpg.service;

public interface SocialEventPublisher {

    /** 好友上线（presence 心跳或登录成功后） */
    void publishFriendOnline(long playerId);

    /** 队伍组建完成 */
    void publishPartyFormed(long leaderId, String partyId, int memberCount);

    /** 助战结算完成 */
    void publishAssistSettled(long borrowerId, long ownerId, boolean victory);

    /** 家园拜访 */
    void publishHomeVisited(long visitorId, long ownerId);

    /** 联机房间互动（表情/观战/点赞等） */
    void publishCoopInteraction(String roomId, String action, long actorId, long targetId);

    /** 通用社交事件：type 如 FRIEND_ADDED / ASSIST_THANKS / HOME_RATED */
    void publish(String eventType, long actorId, long targetId, String payload);
}
