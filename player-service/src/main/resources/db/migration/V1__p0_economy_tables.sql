-- P0 经济账本 / 发奖幂等 / 订单 / Outbox / Inbox（幂等 CREATE IF NOT EXISTS）
CREATE TABLE IF NOT EXISTS wallet_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  currency VARCHAR(16) NOT NULL DEFAULT 'GOLD',
  delta BIGINT NOT NULL,
  balance_before BIGINT NOT NULL,
  balance_after BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(191) NOT NULL,
  operator VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_wallet_idem (idempotency_key),
  KEY idx_wallet_player_time (player_id, created_at),
  KEY idx_wallet_biz (biz_type, biz_no)
);

CREATE TABLE IF NOT EXISTS item_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  item_config_id INT NOT NULL,
  delta INT NOT NULL,
  count_before INT NOT NULL,
  count_after INT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(191) NOT NULL,
  operator VARCHAR(64) NOT NULL DEFAULT 'system',
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_item_ledger_idem (idempotency_key),
  KEY idx_item_ledger_player_time (player_id, created_at),
  KEY idx_item_ledger_biz (biz_type, biz_no)
);

CREATE TABLE IF NOT EXISTS grant_idempotency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  idempotency_key VARCHAR(191) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DONE',
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_grant_idem (player_id, idempotency_key),
  KEY idx_grant_created (created_at)
);

CREATE TABLE IF NOT EXISTS shop_order (
  order_id VARCHAR(64) PRIMARY KEY,
  player_id BIGINT NOT NULL,
  product_id INT NOT NULL,
  product_type VARCHAR(32) NOT NULL DEFAULT '',
  pay_amount BIGINT NOT NULL DEFAULT 0,
  currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
  channel VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  channel_sku VARCHAR(64) NOT NULL DEFAULT '',
  channel_order_id VARCHAR(128) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL,
  created_at BIGINT NOT NULL,
  paid_at BIGINT NOT NULL DEFAULT 0,
  fulfilled_at BIGINT NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(191) NOT NULL DEFAULT '',
  rewards_json TEXT,
  KEY idx_shop_order_player (player_id, created_at),
  KEY idx_shop_order_status (status, paid_at),
  KEY idx_shop_channel_order (channel, channel_order_id)
);

CREATE TABLE IF NOT EXISTS mq_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  topic VARCHAR(128) NOT NULL,
  tag VARCHAR(64) NOT NULL DEFAULT '',
  payload TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NEW',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  KEY idx_outbox_poll (status, next_retry_at, id)
);

CREATE TABLE IF NOT EXISTS mq_inbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  consumer_group VARCHAR(128) NOT NULL,
  message_key VARCHAR(191) NOT NULL,
  topic VARCHAR(128) NOT NULL DEFAULT '',
  payload TEXT,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_inbox_group_key (consumer_group, message_key),
  KEY idx_inbox_created (created_at)
);
