package cn.itcast.demo.mymmorpg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 版本时间线：预下载、强制更新、活动生效/结束时刻。
 */
@Entity
@Table(name = "version_timeline")
public class VersionTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, length = 64)
    private String versionCode;

    @Column(name = "config_version", nullable = false, length = 128)
    private String configVersion;

    @Column(name = "git_commit_sha", length = 64)
    private String gitCommitSha;

    /** 预下载开启时间（毫秒时间戳） */
    @Column(name = "predownload_at_ms", nullable = false)
    private Long predownloadAtMs;

    /** 强制更新时间（毫秒时间戳） */
    @Column(name = "force_update_at_ms", nullable = false)
    private Long forceUpdateAtMs;

    /** 活动/配置生效时间（毫秒时间戳） */
    @Column(name = "effective_at_ms", nullable = false)
    private Long effectiveAtMs;

    /** 活动结束时间（毫秒时间戳，0=无结束） */
    @Column(name = "end_at_ms")
    private Long endAtMs;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "executed_publish", nullable = false)
    private Boolean executedPublish = false;

    @Column(name = "executed_preheat", nullable = false)
    private Boolean executedPreheat = false;

    @Column(name = "executed_warmup", nullable = false)
    private Boolean executedWarmup = false;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getGitCommitSha() {
        return gitCommitSha;
    }

    public void setGitCommitSha(String gitCommitSha) {
        this.gitCommitSha = gitCommitSha;
    }

    public Long getPredownloadAtMs() {
        return predownloadAtMs;
    }

    public void setPredownloadAtMs(Long predownloadAtMs) {
        this.predownloadAtMs = predownloadAtMs;
    }

    public Long getForceUpdateAtMs() {
        return forceUpdateAtMs;
    }

    public void setForceUpdateAtMs(Long forceUpdateAtMs) {
        this.forceUpdateAtMs = forceUpdateAtMs;
    }

    public Long getEffectiveAtMs() {
        return effectiveAtMs;
    }

    public void setEffectiveAtMs(Long effectiveAtMs) {
        this.effectiveAtMs = effectiveAtMs;
    }

    public Long getEndAtMs() {
        return endAtMs;
    }

    public void setEndAtMs(Long endAtMs) {
        this.endAtMs = endAtMs;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getExecutedPublish() {
        return executedPublish;
    }

    public void setExecutedPublish(Boolean executedPublish) {
        this.executedPublish = executedPublish;
    }

    public Boolean getExecutedPreheat() {
        return executedPreheat;
    }

    public void setExecutedPreheat(Boolean executedPreheat) {
        this.executedPreheat = executedPreheat;
    }

    public Boolean getExecutedWarmup() {
        return executedWarmup;
    }

    public void setExecutedWarmup(Boolean executedWarmup) {
        this.executedWarmup = executedWarmup;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
