-- admin-service 版本时间线表
CREATE TABLE IF NOT EXISTS `version_timeline` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_code` VARCHAR(64) NOT NULL COMMENT '版本号（如 2.6.0）',
    `config_version` VARCHAR(128) NOT NULL COMMENT '配置版本标识',
    `git_commit_sha` VARCHAR(64) DEFAULT NULL COMMENT 'Git Commit SHA',
    `predownload_at_ms` BIGINT NOT NULL COMMENT '预下载开启时间',
    `force_update_at_ms` BIGINT NOT NULL COMMENT '强制更新时间',
    `effective_at_ms` BIGINT NOT NULL COMMENT '活动/配置生效时间',
    `end_at_ms` BIGINT DEFAULT NULL COMMENT '活动结束时间',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `executed_publish` TINYINT(1) NOT NULL DEFAULT 0,
    `executed_preheat` TINYINT(1) NOT NULL DEFAULT 0,
    `executed_warmup` TINYINT(1) NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_effective_at` (`effective_at_ms`),
    KEY `idx_version_code` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自动化版本时间线';
