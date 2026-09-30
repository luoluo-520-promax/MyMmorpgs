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
