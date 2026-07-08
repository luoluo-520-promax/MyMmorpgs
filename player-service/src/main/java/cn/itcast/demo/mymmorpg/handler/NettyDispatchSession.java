/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/NettyDispatchSession.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 NettyDispatchSession，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.net.ChannelAttrs; // Netty Channel AttributeKey，挂载 accountId/playerId
import cn.itcast.demo.mymmorpg.net.GameMessage; // Netty 出站帧对象，经 GameMessageEncoder 编码为 [length][msgId][payload]
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议 msgId，afterResponse 识别进场景/切线
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 业务回包统一结构
import cn.itcast.demo.mymmorpg.protocol.RetCode; // protobuf 通用成功码
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp; // 进场景响应，OK 时 bindNetty
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineScRsp; // 切线响应，OK 时刷新 Channel 推送绑定
import cn.itcast.demo.mymmorpg.service.PlayerPushRegistry; // playerId -> ChannelHandlerContext 推送映射
import io.netty.channel.ChannelHandlerContext; // Netty 通道上下文，writeAndFlush 写出 GameMessage
/**
 * Netty TCP 侧 DispatchSession：会话状态存 Channel Attribute，出站经 pipeline 末尾 GameMessageEncoder。
 */
public final class NettyDispatchSession implements DispatchSession { // MessageIoDispatcher 每帧构造，包装 ChannelHandlerContext
    /** 当前客户端连接的 Channel 上下文，accountId/playerId 读写 ChannelAttrs */
    private final ChannelHandlerContext ctx; // writeAndFlush 出站与 Channel 属性读写入口
    /** 推送注册表，进场景成功后 bindNetty，channelInactive 时 unbind */
    private final PlayerPushRegistry playerPushRegistry; // EnterScene/SwitchLine OK 时 bindNetty(pid, ctx)
/**
 * NettyDispatchSession 构造函数
 * 每次调用 channelRead0 方法时会创建一个新的实例
 * @param ctx Netty Channel 上下文，用于绑定网络通道
 * @param playerPushRegistry 服务端推送通道，用于 afterResponse 方法
 */
    public NettyDispatchSession(ChannelHandlerContext ctx, PlayerPushRegistry playerPushRegistry) { // channelRead0 每帧 new 实例
        this.ctx = ctx; // 绑定 Netty Channel 上下文
        this.playerPushRegistry = playerPushRegistry; // afterResponse 绑定服务端推送通道
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public Long accountId() { // AuthFacade 登录态读取
        return ctx.channel().attr(ChannelAttrs.ACCOUNT_ID).get(); // 从 Channel Attribute 读取，跨 pipeline Handler 共享
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void accountId(Long v) { // AuthFacade.accountLogin/logout 写入
        if (v == null) { // 登出时清除 Channel 上的账号绑定
            ctx.channel().attr(ChannelAttrs.ACCOUNT_ID).set(null); // 移除 ACCOUNT_ID Attribute
        } else { // else 分支
            ctx.channel().attr(ChannelAttrs.ACCOUNT_ID).set(v); // 登录成功写入 accountId
        } // else 代码块结束
    } // else 代码块结束

    @Override // 实现接口/父类方法
    public Long playerId() { // DispatchThreadModel dispatchKey 来源
        return ctx.channel().attr(ChannelAttrs.PLAYER_ID).get(); // 选角成功后 AuthFacade 写入
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void playerId(Long v) { // AuthFacade.selectPlayer/logout 写入
        if (v == null) { // 登出或切账号时清除
            ctx.channel().attr(ChannelAttrs.PLAYER_ID).set(null); // 清除 PLAYER_ID，dispatchKey 回退 marker.hashCode
        } else { // else 分支
            ctx.channel().attr(ChannelAttrs.PLAYER_ID).set(v); // 作为 DispatchThreadModel 的 dispatchKey
        } // else 代码块结束
    } // else 代码块结束

    @Override // 实现接口/父类方法
    public String marker() { // IdempotencyService 幂等键前缀
        return "netty:" + ctx.channel().id().asLongText(); // 全局唯一 ChannelId，选角多连接互斥 marker
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void send(ProtocolMessage msg) { // ClientRequestTask/幂等 replay 出站
        if (msg == null) { // 防御空包
            return; // 避免 writeAndFlush NPE
        } // 编译单元结束

        ctx.writeAndFlush(new GameMessage(msg.msgId(), msg.payload())); // pipeline 出站经 GameMessageEncoder 加长度前缀
    } // 编译单元结束

    @Override // 实现接口/父类方法
    public void afterResponse(int requestMsgId, ProtocolMessage response) { // ClientRequestTask send 前调用，dispatch stripe 线程
        if (playerPushRegistry == null || response == null) { // 未装配推送表或空回包
            return; // 跳过推送绑定
        } // 编译单元结束

        Long pid = playerId(); // 当前 Channel 绑定的玩家
        if (pid == null || pid <= 0) { // 未选角不绑定推送
            return; // 避免误推
        } // 编译单元结束

        try { // 代码块开始
            if (requestMsgId == MessageId.ENTER_SCENE_CS_REQ) { // 进场景 CsReq 对应回包
                EnterSceneScRsp rsp = EnterSceneScRsp.parseFrom(response.payload()); // protobuf 解码 EnterSceneScRsp
                if (rsp.getRetcode() == RetCode.OK) { // 进场景成功
                    playerPushRegistry.bindNetty(pid, ctx); // 服务端广播/AOI 推送经此 Channel 下发
                } // 块 代码块结束
            } else if (requestMsgId == MessageId.SWITCH_LINE_CS_REQ) { // 切分线 CsReq 对应回包
                SwitchLineScRsp rsp = SwitchLineScRsp.parseFrom(response.payload()); // protobuf 解码 SwitchLineScRsp
                if (rsp.getRetcode() == RetCode.OK) { // 切线成功
                    playerPushRegistry.bindNetty(pid, ctx); // 刷新 Netty 推送通道到新分线上下文
                } // 编译单元结束
            } // 编译单元结束
        } catch (Exception ignore) { // protobuf 损坏不影响主流程 send
            // 解码失败不影响主流程 ScRsp 出站
        } // 编译单元结束
    } // 编译单元结束
} // 编译单元结束
