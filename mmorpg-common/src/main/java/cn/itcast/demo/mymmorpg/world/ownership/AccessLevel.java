package cn.itcast.demo.mymmorpg.world.ownership;

/**
 * 联机世界交互访问级别：防止访客「偷世界」。
 * <ul>
 *   <li>HOST_ONLY — 仅房主可采集/开箱/接关键任务</li>
 *   <li>GUEST_READ — 访客可见但不可交互（只读镜像）</li>
 *   <li>ALL_SHARE — 房主与访客均可交互（每人独立进度或共享，由物权策略决定）</li>
 * </ul>
 */
public enum AccessLevel {
    HOST_ONLY,
    GUEST_READ,
    ALL_SHARE
}
