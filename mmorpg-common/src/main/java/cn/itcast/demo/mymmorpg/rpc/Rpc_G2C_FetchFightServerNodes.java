/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/Rpc_G2C_FetchFightServerNodes.java
 * 类型：类
 * 职责：游戏服（Game）向中心服（Center）拉取已注册战斗服节点目录的 RPC 请求消息体。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * 游戏服 → 中心服：请求拉取当前所有在线战斗服节点列表。
 * <p>消息体为空，类型名（kind）即路由标识；中心服收到后返回 {@link RpcC2G_FightServerNodes}。
 */
public class Rpc_G2C_FetchFightServerNodes {
}
