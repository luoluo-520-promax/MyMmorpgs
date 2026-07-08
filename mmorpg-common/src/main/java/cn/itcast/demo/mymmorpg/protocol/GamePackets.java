/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/GamePackets.java
 * 类型：类
 * 职责：集中定义所有客户端上行请求包类型，并标注 {@link MessageMeta} 供路由反射解析。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 入站消息类型注册表

import cn.itcast.demo.mymmorpg.protocol.MessageMeta; // 标记模块内 cmd，与 Modules 组合成 msgId

/**
 * 为满足「每个消息类均有 {@link MessageMeta}」的约束，这里集中定义所有客户端请求包类型。
 * <p>每个嵌套类只承载原始 payload，具体 Protobuf 反序列化由 Facade 内部 {@code parseFrom} 完成。</p>
 */
public final class GamePackets { // final 工具容器，不对外继承

    /**
     * 私有构造器，禁止实例化。
     */
    private GamePackets() { // 仅通过嵌套静态类引用消息类型
    }

    // -------- 0xx：登录 / 选角 / 登出（module = {@link Modules#AUTH}） --------

    /** 账号登录：msgId = {@link MessageId#ACCOUNT_LOGIN_CS_REQ} */
    @MessageMeta(cmd = 1) // 模块内命令号 1
    public static final class AccountLoginCsReq extends PayloadPacket { // 客户端上行：账号+密码登录
        public AccountLoginCsReq(byte[] p) { // Netty 解码后传入 Protobuf 字节
            super(p); // 保存至 PayloadPacket.payload
        }
    }

    /** 选择角色：msgId = {@link MessageId#SELECT_PLAYER_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class SelectPlayerCsReq extends PayloadPacket { // 登录后选择 playerId 进入游戏
        public SelectPlayerCsReq(byte[] p) {
            super(p);
        }
    }

    /** 角色登出：msgId = {@link MessageId#PLAYER_LOGOUT_CS_REQ} */
    @MessageMeta(cmd = 5)
    public static final class PlayerLogoutCsReq extends PayloadPacket { // 主动退出当前角色会话
        public PlayerLogoutCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 1xx：场景（module = {@link Modules#SCENE}） --------

    /** 进入场景：msgId = {@link MessageId#ENTER_SCENE_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class EnterSceneCsReq extends PayloadPacket { // 传 sceneId、分线等
        public EnterSceneCsReq(byte[] p) {
            super(p);
        }
    }

    /** 当前场景信息：msgId = {@link MessageId#GET_CUR_SCENE_INFO_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class GetCurSceneInfoCsReq extends PayloadPacket { // 查询玩家所在场景快照
        public GetCurSceneInfoCsReq(byte[] p) {
            super(p);
        }
    }

    /** 移动：msgId = {@link MessageId#MOVE_CS_REQ} */
    @MessageMeta(cmd = 5)
    public static final class MoveCsReq extends PayloadPacket { // 上报坐标或位移向量
        public MoveCsReq(byte[] p) {
            super(p);
        }
    }

    /** 切换分线：msgId = {@link MessageId#SWITCH_LINE_CS_REQ} */
    @MessageMeta(cmd = 8)
    public static final class SwitchLineCsReq extends PayloadPacket { // 同场景换 lineId
        public SwitchLineCsReq(byte[] p) {
            super(p);
        }
    }

    /** 附近实体：msgId = {@link MessageId#GET_NEARBY_ENTITIES_CS_REQ} */
    @MessageMeta(cmd = 10)
    public static final class GetNearbyEntitiesCsReq extends PayloadPacket { // 拉取视野内 Actor 列表
        public GetNearbyEntitiesCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 2xx：战斗（module = {@link Modules#BATTLE}，可跨服路由） --------

    /**
     * 标记战斗类消息，供 {@code RpcClientRouter} 等识别并转发到战斗服。
     */
    public interface BattleMessage { } // 标记接口，无方法，仅用于 instanceof / 类型约束

    /** 开始战斗：msgId = {@link MessageId#BATTLE_START_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class BattleStartCsReq extends PayloadPacket implements BattleMessage { // 对场景怪物开战
        public BattleStartCsReq(byte[] p) {
            super(p);
        }
    }

    /** 战斗操作：msgId = {@link MessageId#BATTLE_ACTION_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class BattleActionCsReq extends PayloadPacket implements BattleMessage { // 回合内普攻/技能
        public BattleActionCsReq(byte[] p) {
            super(p);
        }
    }

    /** 战斗结束：msgId = {@link MessageId#BATTLE_END_CS_REQ} */
    @MessageMeta(cmd = 6)
    public static final class BattleEndCsReq extends PayloadPacket implements BattleMessage { // 结算或退出战斗
        public BattleEndCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 3xx：技能（module = {@link Modules#SKILL}） --------

    /** 技能列表：msgId = {@link MessageId#GET_PLAYER_SKILLS_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class GetPlayerSkillsCsReq extends PayloadPacket { // 查询已学技能与等级
        public GetPlayerSkillsCsReq(byte[] p) {
            super(p);
        }
    }

    /** 学习技能：msgId = {@link MessageId#LEARN_SKILL_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class LearnSkillCsReq extends PayloadPacket { // 消耗资源学习新技能
        public LearnSkillCsReq(byte[] p) {
            super(p);
        }
    }

    /** 释放技能：msgId = {@link MessageId#CAST_SKILL_CS_REQ} */
    @MessageMeta(cmd = 5)
    public static final class CastSkillCsReq extends PayloadPacket { // 场景内主动施法
        public CastSkillCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 4xx：Buff（module = {@link Modules#BUFF}） --------

    /** 实体 Buff 列表：msgId = {@link MessageId#GET_ENTITY_BUFFS_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class GetEntityBuffsCsReq extends PayloadPacket { // 查询指定 entityId 身上 Buff
        public GetEntityBuffsCsReq(byte[] p) {
            super(p);
        }
    }

    /** 移除 Buff：msgId = {@link MessageId#REMOVE_BUFF_CS_REQ} */
    @MessageMeta(cmd = 6)
    public static final class RemoveBuffCsReq extends PayloadPacket { // 主动驱散（若配置允许）
        public RemoveBuffCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 6xx：聊天（module = {@link Modules#CHAT}） --------

    /** 发送聊天：msgId = {@link MessageId#SEND_CHAT_MSG_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class SendChatMsgCsReq extends PayloadPacket { // 频道或私聊内容
        public SendChatMsgCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 7xx：背包 / 道具（module = {@link Modules#BAG}） --------

    /** 背包信息：msgId = {@link MessageId#GET_BAG_INFO_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class GetBagInfoCsReq extends PayloadPacket { // 全量或增量拉取背包
        public GetBagInfoCsReq(byte[] p) {
            super(p);
        }
    }

    /** 使用道具：msgId = {@link MessageId#USE_ITEM_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class UseItemCsReq extends PayloadPacket { // 指定格子与数量使用
        public UseItemCsReq(byte[] p) {
            super(p);
        }
    }

    /** 丢弃道具：msgId = {@link MessageId#DISCARD_ITEM_CS_REQ} */
    @MessageMeta(cmd = 5)
    public static final class DiscardItemCsReq extends PayloadPacket { // 销毁背包内物品
        public DiscardItemCsReq(byte[] p) {
            super(p);
        }
    }

    /** 背包排序：msgId = {@link MessageId#SORT_BAG_CS_REQ} */
    @MessageMeta(cmd = 7)
    public static final class SortBagCsReq extends PayloadPacket { // 按规则重排格子
        public SortBagCsReq(byte[] p) {
            super(p);
        }
    }

    /** 出售道具：msgId = {@link MessageId#SELL_ITEM_CS_REQ} */
    @MessageMeta(cmd = 9)
    public static final class SellItemCsReq extends PayloadPacket { // 卖给 NPC 或商店
        public SellItemCsReq(byte[] p) {
            super(p);
        }
    }

    // -------- 8xx：活动（module = {@link Modules#ACTIVITY}） --------

    /** 活动列表：msgId = {@link MessageId#GET_ACTIVITY_LIST_CS_REQ} */
    @MessageMeta(cmd = 1)
    public static final class GetActivityListCsReq extends PayloadPacket { // 当前开放活动摘要
        public GetActivityListCsReq(byte[] p) {
            super(p);
        }
    }

    /** 活动详情：msgId = {@link MessageId#GET_ACTIVITY_DETAIL_CS_REQ} */
    @MessageMeta(cmd = 3)
    public static final class GetActivityDetailCsReq extends PayloadPacket { // 单活动进度与奖励档位
        public GetActivityDetailCsReq(byte[] p) {
            super(p);
        }
    }

    /** 领取奖励：msgId = {@link MessageId#CLAIM_ACTIVITY_REWARD_CS_REQ} */
    @MessageMeta(cmd = 5)
    public static final class ClaimActivityRewardCsReq extends PayloadPacket { // 按 rewardIndex 领奖
        public ClaimActivityRewardCsReq(byte[] p) {
            super(p);
        }
    }
}
