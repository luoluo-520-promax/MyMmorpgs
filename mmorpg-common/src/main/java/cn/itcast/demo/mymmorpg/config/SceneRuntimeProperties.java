package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 大世界分线 / Zone 运行时参数。
 */
@ConfigurationProperties(prefix = "game.scene")
public class SceneRuntimeProperties {

    /** 单线玩家硬顶，满员后禁止进入 */
    private int maxPlayersPerLine = 100;
    /** 单线软顶：达到后仍可进，但附掉落引导 Buff */
    private int softPlayersPerLine = 80;
    /** 软顶引导 Buff Id */
    private String softCapBuffId = "line_overflow_drop_bonus_5";
    /** AOI 更新合并窗口（毫秒） */
    private long aoiBatchWindowMs = 150L;
    /** AOI 近距全量同步半径（米） */
    private float aoiNearDistance = 10f;
    /** AOI 中距降频半径（米） */
    private float aoiMidDistance = 30f;
    /** AOI 远距低频半径（米） */
    private float aoiFarDistance = 50f;
    /** Gameplay Tick 分片数 */
    private int gameplayTickSlices = 20;
    /** 单分片 Tick 预算（毫秒） */
    private long gameplayTickBudgetMs = 5L;
    /** 单地图最大分线数（可超过 map_config.default_lines） */
    private int maxLinesPerMap = 20;
    /** 空线回收宽限（毫秒） */
    private long emptyLineTtlMs = 300_000L;
    /** 空线回收扫描周期（毫秒） */
    private long emptyLineGcMs = 30_000L;
    /** 断线重连快照 TTL（毫秒） */
    private long reconnectGraceMs = 60_000L;
    /** 重连成功后保护期（毫秒）：原地无敌/隐身，默认 30 秒 */
    private long reconnectProtectionMs = 30_000L;
    /** 是否向 Center 注册本节点承载的场景 */
    private boolean registerToCenter = false;
    /** 本节点承载的 sceneId 列表；空且 registerToCenter=true 时注册全部 map */
    private List<Integer> ownedSceneIds = new ArrayList<>();
    /** 节点心跳间隔（毫秒） */
    private long nodeHeartbeatMs = 15_000L;
    /** Center 判定节点失联超时（毫秒） */
    private long nodeStaleMs = 45_000L;

    public int getMaxPlayersPerLine() {
        return maxPlayersPerLine;
    }

    public void setMaxPlayersPerLine(int maxPlayersPerLine) {
        this.maxPlayersPerLine = maxPlayersPerLine;
    }

    public int getSoftPlayersPerLine() {
        return softPlayersPerLine;
    }

    public void setSoftPlayersPerLine(int softPlayersPerLine) {
        this.softPlayersPerLine = softPlayersPerLine;
    }

    public String getSoftCapBuffId() {
        return softCapBuffId;
    }

    public void setSoftCapBuffId(String softCapBuffId) {
        this.softCapBuffId = softCapBuffId;
    }

    public long getAoiBatchWindowMs() {
        return aoiBatchWindowMs;
    }

    public void setAoiBatchWindowMs(long aoiBatchWindowMs) {
        this.aoiBatchWindowMs = aoiBatchWindowMs;
    }

    public float getAoiNearDistance() {
        return aoiNearDistance;
    }

    public void setAoiNearDistance(float aoiNearDistance) {
        this.aoiNearDistance = aoiNearDistance;
    }

    public float getAoiMidDistance() {
        return aoiMidDistance;
    }

    public void setAoiMidDistance(float aoiMidDistance) {
        this.aoiMidDistance = aoiMidDistance;
    }

    public float getAoiFarDistance() {
        return aoiFarDistance;
    }

    public void setAoiFarDistance(float aoiFarDistance) {
        this.aoiFarDistance = aoiFarDistance;
    }

    public int getGameplayTickSlices() {
        return gameplayTickSlices;
    }

    public void setGameplayTickSlices(int gameplayTickSlices) {
        this.gameplayTickSlices = gameplayTickSlices;
    }

    public long getGameplayTickBudgetMs() {
        return gameplayTickBudgetMs;
    }

    public void setGameplayTickBudgetMs(long gameplayTickBudgetMs) {
        this.gameplayTickBudgetMs = gameplayTickBudgetMs;
    }

    public int getMaxLinesPerMap() {
        return maxLinesPerMap;
    }

    public void setMaxLinesPerMap(int maxLinesPerMap) {
        this.maxLinesPerMap = maxLinesPerMap;
    }

    public long getEmptyLineTtlMs() {
        return emptyLineTtlMs;
    }

    public void setEmptyLineTtlMs(long emptyLineTtlMs) {
        this.emptyLineTtlMs = emptyLineTtlMs;
    }

    public long getEmptyLineGcMs() {
        return emptyLineGcMs;
    }

    public void setEmptyLineGcMs(long emptyLineGcMs) {
        this.emptyLineGcMs = emptyLineGcMs;
    }

    public long getReconnectGraceMs() {
        return reconnectGraceMs;
    }

    public void setReconnectGraceMs(long reconnectGraceMs) {
        this.reconnectGraceMs = reconnectGraceMs;
    }

    public long getReconnectProtectionMs() {
        return reconnectProtectionMs;
    }

    public void setReconnectProtectionMs(long reconnectProtectionMs) {
        this.reconnectProtectionMs = reconnectProtectionMs;
    }

    public boolean isRegisterToCenter() {
        return registerToCenter;
    }

    public void setRegisterToCenter(boolean registerToCenter) {
        this.registerToCenter = registerToCenter;
    }

    public List<Integer> getOwnedSceneIds() {
        return ownedSceneIds;
    }

    public void setOwnedSceneIds(List<Integer> ownedSceneIds) {
        this.ownedSceneIds = ownedSceneIds == null ? new ArrayList<>() : ownedSceneIds;
    }

    public long getNodeHeartbeatMs() {
        return nodeHeartbeatMs;
    }

    public void setNodeHeartbeatMs(long nodeHeartbeatMs) {
        this.nodeHeartbeatMs = nodeHeartbeatMs;
    }

    public long getNodeStaleMs() {
        return nodeStaleMs;
    }

    public void setNodeStaleMs(long nodeStaleMs) {
        this.nodeStaleMs = nodeStaleMs;
    }
}
