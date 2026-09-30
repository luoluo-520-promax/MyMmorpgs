-- activity_db schema（与 JPA Activity 实体对齐；源文件见 activity-service）
USE activity_db;

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

CREATE TABLE IF NOT EXISTS `mq_inbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `consumer_group` VARCHAR(128) NOT NULL,
    `message_key` VARCHAR(191) NOT NULL,
    `topic` VARCHAR(128) NOT NULL DEFAULT '',
    `payload` TEXT DEFAULT NULL,
    `created_at` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inbox_group_key` (`consumer_group`, `message_key`),
    KEY `idx_inbox_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 消费 Inbox 幂等表';
