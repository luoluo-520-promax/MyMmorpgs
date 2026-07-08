/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/unit/ActivityMessageIdSanityTest.java
 * 2) 所属模块：activity-service / test / unit
 * 3) 主要职责：不启动 Spring，快速校验活动相关协议消息号常量
 * 4) 系统位置：轻量单元测试，防止 MessageId 与协议文档不一致
 * 5) 变更建议：新增活动协议时在此补充消息号断言
 */
package cn.itcast.demo.mymmorpg.service.unit;

import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息号常量类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test; // TestNG 测试注解

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 断言

/**
 * 无 Spring：活动相关协议号与公共常量快速校验。
 */
public class ActivityMessageIdSanityTest { // 协议号冒烟测试

    private static final Logger log = LoggerFactory.getLogger(ActivityMessageIdSanityTest.class);

    /**
     * 活动列表请求/响应消息号应为 801/802。
     */
    @Test
    public void activityListMessageIds() { // 活动列表请求/响应号
        int listReq = MessageId.GET_ACTIVITY_LIST_CS_REQ;
        int listRsp = MessageId.GET_ACTIVITY_LIST_SC_RSP;
        log.info("[测试开始] 场景=活动列表消息号 | GET_ACTIVITY_LIST_CS_REQ={} | GET_ACTIVITY_LIST_SC_RSP={}",
                listReq, listRsp);

        assertThat(listReq).isEqualTo(801); // 客户端拉列表
        assertThat(listRsp).isEqualTo(802); // 服务端列表响应

        log.info("[测试断言] 场景=活动列表消息号 | 期望=[801, 802] | 实际=[{}, {}]", listReq, listRsp);
    }

    /**
     * 活动详情、领奖、状态推送消息号校验。
     */
    @Test
    public void activityDetailClaimAndNotifyMessageIds() {
        int detailReq = MessageId.GET_ACTIVITY_DETAIL_CS_REQ;
        int detailRsp = MessageId.GET_ACTIVITY_DETAIL_SC_RSP;
        int claimReq = MessageId.CLAIM_ACTIVITY_REWARD_CS_REQ;
        int claimRsp = MessageId.CLAIM_ACTIVITY_REWARD_SC_RSP;
        int notify = MessageId.ACTIVITY_STATUS_SC_NOTIFY;

        log.info("[测试开始] 场景=活动详情/领奖/推送消息号 | detailReq={} | detailRsp={} | claimReq={} | claimRsp={} | notify={}",
                detailReq, detailRsp, claimReq, claimRsp, notify);

        assertThat(detailReq).isEqualTo(803);
        assertThat(detailRsp).isEqualTo(804);
        assertThat(claimReq).isEqualTo(805);
        assertThat(claimRsp).isEqualTo(806);
        assertThat(notify).isEqualTo(807);

        log.info("[测试断言] 场景=活动详情/领奖/推送消息号 | 期望=[803,804,805,806,807] | 实际=[{},{},{},{},{}]",
                detailReq, detailRsp, claimReq, claimRsp, notify);
    }
}
