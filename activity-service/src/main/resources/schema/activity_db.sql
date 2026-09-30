-- activity-service 独立库初始化脚本（唯一权威 DDL，与 JPA Activity 实体对齐）
-- 进度数据以 Redis JSON 为主，不再建 player_activity_progress 表。
-- 创建库：CREATE DATABASE IF NOT EXISTS activity_db DEFAULT CHARSET utf8mb4;
-- 权威说明见 docs/db-migration.md

CREATE TABLE IF NOT EXISTS `activity` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动实例ID',
    `type` TINYINT UNSIGNED NOT NULL COMMENT '活动类型（对应 ActivityTypes 枚举）',
    `opened` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '活动是否开启（0=关闭，1=开启）',
    `data` TEXT DEFAULT NULL COMMENT '活动配置/进度数据（序列化字符串）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_id` (`type`, `id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动数据表';

-- P0：MQ Inbox 幂等（支付累充 / 战斗结算投影）
CREATE TABLE IF NOT EXISTS `mq_inbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `consumer_group` VARCHAR(128) NOT NULL COMMENT '消费组',
    `message_key` VARCHAR(191) NOT NULL COMMENT '幂等键（如 shop:orderId / battle:end:battleId）',
    `topic` VARCHAR(128) NOT NULL DEFAULT '',
    `payload` TEXT DEFAULT NULL,
    `created_at` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbox_group_key` (`consumer_group`, `message_key`),
    KEY `idx_inbox_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 消费 Inbox 幂等表';

CREATE TABLE IF NOT EXISTS `activity_snapshot` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `player_id` BIGINT UNSIGNED NOT NULL COMMENT '玩家 ID',
    `version_code` VARCHAR(64) NOT NULL COMMENT '版本号',
    `snapshot_type` VARCHAR(32) NOT NULL COMMENT '快照类型：activity_progress / activity_token',
    `payload_json` TEXT DEFAULT NULL COMMENT '快照 JSON',
    `created_at_ms` BIGINT NOT NULL COMMENT '快照时间戳',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_player_version` (`player_id`, `version_code`),
    KEY `idx_created_at` (`created_at_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动数据快照（版本回退用）';
