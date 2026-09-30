-- 背包与道具系统：在已有 mmorpg 库执行（与 JPA validate 对齐）
-- 若列已存在，可跳过对应 ALTER。

ALTER TABLE `player`
    ADD COLUMN `gold` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '金币（出售等）' AFTER `vip_right`;

ALTER TABLE `player`
    ADD COLUMN `exp` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计经验' AFTER `gold`;

CREATE TABLE IF NOT EXISTS `item_config` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '物品模板ID',
    `name` VARCHAR(64) NOT NULL COMMENT '物品名称',
    `kind` INT NOT NULL COMMENT '物品类型（1=消耗品 2=装备等）',
    `stack_limit` INT NOT NULL DEFAULT 1 COMMENT '堆叠上限',
    `level_required` INT NOT NULL DEFAULT 0 COMMENT '使用/装备等级要求',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `price` INT NOT NULL DEFAULT 0 COMMENT '购买价格',
    `sell_price` INT NOT NULL DEFAULT 0 COMMENT '出售价格',
    `effect_params` VARCHAR(255) DEFAULT NULL COMMENT '效果参数 JSON，如 {"exp":1000}',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品配置表';

CREATE TABLE IF NOT EXISTS `player_bag_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物品唯一实例ID（item_uid）',
    `player_id` BIGINT NOT NULL COMMENT '玩家ID',
    `item_config_id` INT NOT NULL COMMENT '物品模板ID，对应 item_config.id',
    `count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前数量',
    `bind` INT NOT NULL DEFAULT 0 COMMENT '0=未绑定 1=绑定',
    `slot_index` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '格子序号（整理时重排）',
    `equip_slot` INT NOT NULL DEFAULT 0 COMMENT '0未穿戴 >0装备槽',
    `affix_blob` BLOB NULL COMMENT '装备随机主/副词条 JSON',
    `suit_id` INT NOT NULL DEFAULT 0 COMMENT '套装ID预留（本轮不匹配）',
    PRIMARY KEY (`id`),
    KEY `idx_player_slot` (`player_id`, `slot_index`),
    CONSTRAINT `fk_bag_player` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bag_item_config` FOREIGN KEY (`item_config_id`) REFERENCES `item_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='玩家背包物品实例';

-- 已有库增量：套装字段预留
-- ALTER TABLE `player_bag_item` ADD COLUMN `suit_id` INT NOT NULL DEFAULT 0 COMMENT '套装ID预留';
