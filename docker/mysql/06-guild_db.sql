-- guild_db schema（由 docker-compose 挂载；源文件见 guild-service）
USE guild_db;

CREATE TABLE IF NOT EXISTS `guild` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(64) NOT NULL,
    `level` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1~3',
    `exp` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `fund` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '公会资金',
    `notice` VARCHAR(512) NOT NULL DEFAULT '',
    `leader_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_guild_name` (`name`),
    KEY `idx_guild_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公会主表';

CREATE TABLE IF NOT EXISTS `guild_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `guild_id` BIGINT UNSIGNED NOT NULL,
    `player_id` BIGINT NOT NULL,
    `role` TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '1会长 2副会 3成员',
    `contribution` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_guild_player` (`player_id`),
    KEY `idx_guild_member_guild` (`guild_id`),
    CONSTRAINT `fk_guild_member_guild` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公会成员';

CREATE TABLE IF NOT EXISTS `guild_tech` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `guild_id` BIGINT UNSIGNED NOT NULL,
    `tech_line` VARCHAR(32) NOT NULL COMMENT 'ATTACK/HP/STAMINA',
    `tech_level` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0~3',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_guild_tech_line` (`guild_id`, `tech_line`),
    CONSTRAINT `fk_guild_tech_guild` FOREIGN KEY (`guild_id`) REFERENCES `guild` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公会科技树';
