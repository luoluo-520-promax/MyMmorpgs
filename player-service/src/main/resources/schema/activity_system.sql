-- 活动系统：在 mmorpg 库执行（与 JPA validate 对齐）

CREATE TABLE IF NOT EXISTS `activity` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动实例ID',
    `type` TINYINT UNSIGNED NOT NULL COMMENT '活动类型（对应 ActivityTypes 枚举）',
    `opened` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '活动是否开启（0=关闭，1=开启）',
    `data` TEXT DEFAULT NULL COMMENT '活动进度数据（序列化字符串，子类扩展字段）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_id` (`type`, `id`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动数据表';
