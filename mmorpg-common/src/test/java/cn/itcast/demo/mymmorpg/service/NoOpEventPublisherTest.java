/**
 * 文件说明：NoOp 事件发布器单元测试。
 * 职责：验证 MQ 关闭时空实现发布器可安全调用且不抛异常。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

public class NoOpEventPublisherTest {

    private static final Logger log = LoggerFactory.getLogger(NoOpEventPublisherTest.class);

    @Test
    public void noOpPlayerEventPublisher_safeInvoke() {
        long accountId = 10001L;
        long playerId = 20002L;
        String accountName = "test-account";
        String playerName = "test-player";
        int logoutReason = 1;
        PlayerEventPublisher publisher = new NoOpPlayerEventPublisher();
        log.info("[测试开始] 场景=NoOp玩家事件 | accountId={} | playerId={} | accountName={} | playerName={} | logoutReason={}",
                accountId, playerId, accountName, playerName, logoutReason);

        assertThatCode(() -> {
            publisher.publishAccountLogin(accountId, accountName);
            publisher.publishPlayerEnter(accountId, playerId, playerName);
            publisher.publishPlayerLogout(accountId, playerId, logoutReason);
        }).doesNotThrowAnyException();

        log.info("[测试断言] 场景=NoOp玩家事件 | 期望=三次调用均不抛异常");
    }

    @Test
    public void noOpSceneEventPublisher_safeInvoke() {
        long playerId = 30003L;
        int sceneId = 1001;
        int lineId = 2;
        int fromLine = 1;
        int toLine = 3;
        float x = 12.5f;
        float y = 0.0f;
        float z = -8.25f;
        SceneEventPublisher publisher = new NoOpSceneEventPublisher();
        log.info("[测试开始] 场景=NoOp场景事件 | playerId={} | sceneId={} | lineId={} | fromLine={} | toLine={} | pos=({},{},{})",
                playerId, sceneId, lineId, fromLine, toLine, x, y, z);

        assertThatCode(() -> {
            publisher.publishEnterScene(playerId, sceneId, lineId);
            publisher.publishLeaveScene(playerId, sceneId);
            publisher.publishSwitchLine(playerId, sceneId, fromLine, toLine);
            publisher.publishMove(playerId, sceneId, x, y, z);
        }).doesNotThrowAnyException();

        log.info("[测试断言] 场景=NoOp场景事件 | 期望=四次调用均不抛异常");
    }

    @Test
    public void noOpItemEventPublisher_safeInvoke() {
        long playerId = 40004L;
        long itemUid = 88001L;
        int itemConfigId = 5001;
        int usedCount = 2;
        int soldCount = 1;
        long currencyGained = 150L;
        ItemEventPublisher publisher = new NoOpItemEventPublisher();
        log.info("[测试开始] 场景=NoOp道具事件 | playerId={} | itemUid={} | itemConfigId={} | usedCount={} | soldCount={} | currencyGained={}",
                playerId, itemUid, itemConfigId, usedCount, soldCount, currencyGained);

        assertThatCode(() -> {
            publisher.publishItemUsed(playerId, itemUid, itemConfigId, usedCount);
            publisher.publishItemSold(playerId, itemUid, itemConfigId, soldCount, currencyGained);
            publisher.publishItemDiscarded(playerId, itemUid, itemConfigId, usedCount);
        }).doesNotThrowAnyException();

        log.info("[测试断言] 场景=NoOp道具事件 | 期望=三次调用均不抛异常");
    }

    @Test
    public void noOpChatEventPublisher_safeInvoke() {
        long senderId = 50005L;
        int channel = 1;
        long targetId = 50006L;
        int msgType = 0;
        String content = "hello-mmorpg";
        long serverTs = 1719500000000L;
        ChatEventPublisher publisher = new NoOpChatEventPublisher();
        log.info("[测试开始] 场景=NoOp聊天事件 | senderId={} | channel={} | targetId={} | msgType={} | serverTs={}",
                senderId, channel, targetId, msgType, serverTs);

        assertThatCode(() -> publisher.publishChatSent(senderId, channel, targetId, msgType, content, serverTs))
                .doesNotThrowAnyException();

        log.info("[测试断言] 场景=NoOp聊天事件 | 期望=调用不抛异常");
    }

    @Test
    public void noOpSkillEventPublisher_safeInvoke() {
        long playerId = 60006L;
        int skillId = 1001;
        long targetEntityId = 70007L;
        int damage = 320;
        int heal = 0;
        SkillEventPublisher publisher = new NoOpSkillEventPublisher();
        log.info("[测试开始] 场景=NoOp技能事件 | playerId={} | skillId={} | targetEntityId={} | damage={} | heal={}",
                playerId, skillId, targetEntityId, damage, heal);

        assertThatCode(() -> {
            publisher.publishSkillLearned(playerId, skillId);
            publisher.publishSkillCast(playerId, skillId, targetEntityId, damage, heal);
        }).doesNotThrowAnyException();

        log.info("[测试断言] 场景=NoOp技能事件 | 期望=两次调用均不抛异常");
    }

    @Test
    public void noOpSocialEventPublisher_safeInvoke() {
        SocialEventPublisher publisher = new NoOpSocialEventPublisher();
        assertThatCode(() -> {
            publisher.publishFriendOnline(1L);
            publisher.publishPartyFormed(1L, "pty-1", 2);
            publisher.publishAssistSettled(1L, 2L, true);
            publisher.publishHomeVisited(1L, 2L);
            publisher.publishCoopInteraction("coop-1", "LIKE", 1L, 2L);
            publisher.publish("FRIEND_ADDED", 1L, 2L, "");
        }).doesNotThrowAnyException();
    }
}
