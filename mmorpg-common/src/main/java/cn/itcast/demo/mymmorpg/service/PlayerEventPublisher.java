/**
 * 玩家会话生命周期事件发布抽象：账号登录、选角进入、登出时触发，
 * RocketMQ 开启时投递至 PLAYER_LOGIN/PLAYER_LOGOUT 等 Topic，关闭时仅打 debug 日志。
 */
package cn.itcast.demo.mymmorpg.service;

public interface PlayerEventPublisher { // 玩家会话事件对外发布契约：连接账号、角色与在线状态链路

    /** 账号级登录成功（尚未选角进入游戏世界） */
    void publishAccountLogin(long accountId, String accountName); // 记录账号进入大厅，用于登录监控与账号活跃分析

    /** 玩家选角完成并进入游戏（角色在线开始） */
    void publishPlayerEnter(long accountId, long playerId, String playerName); // 记录角色正式上线，触发在线人数、公告和初始化流程

    /**
     * 玩家登出或断线。
     *
     * @param reason 登出原因码（主动退出/踢线/超时等），供在线时长与流失分析
     */
    void publishPlayerLogout(long accountId, Long playerId, int reason); // 记录角色下线，供结算保存、会话回收与流失归因
}
