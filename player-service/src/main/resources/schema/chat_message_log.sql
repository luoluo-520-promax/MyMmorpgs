-- 聊天审计表（jpa.hibernate.ddl-auto=validate 时需先执行）
CREATE TABLE IF NOT EXISTS `chat_message_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sender_id` BIGINT NOT NULL,
    `channel` INT NOT NULL,
    `target_id` BIGINT NULL,
    `msg_type` INT NOT NULL,
    `content` VARCHAR(512) NOT NULL,
    `server_ts` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_chat_sender` (`sender_id`),
    KEY `idx_chat_ts` (`server_ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
