CREATE TABLE IF NOT EXISTS mq_inbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  consumer_group VARCHAR(128) NOT NULL,
  message_key VARCHAR(191) NOT NULL,
  topic VARCHAR(128) NOT NULL DEFAULT '',
  payload TEXT DEFAULT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inbox_group_key (consumer_group, message_key),
  KEY idx_inbox_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
