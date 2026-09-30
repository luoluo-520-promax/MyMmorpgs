-- player-service 独立库初始化脚本（与 JPA 实体 validate 对齐）
-- 建议在 MySQL 中先创建数据库：CREATE DATABASE IF NOT EXISTS player_db DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_name VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_time DATETIME NULL,
  banned TINYINT(1) NOT NULL DEFAULT 0,
  ban_reason VARCHAR(255) NULL,
  ban_until DATETIME NULL
);

CREATE TABLE IF NOT EXISTS player (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL UNIQUE,
  level INT NOT NULL DEFAULT 1,
  vip_right INT NOT NULL DEFAULT 0,
  gold BIGINT NOT NULL DEFAULT 0,
  exp BIGINT NOT NULL DEFAULT 0,
  strength INT NOT NULL DEFAULT 10,
  agility INT NOT NULL DEFAULT 10,
  intelligence INT NOT NULL DEFAULT 10,
  talent_points INT NOT NULL DEFAULT 0,
  talent_json VARCHAR(512) DEFAULT '{}',
  power_score INT NOT NULL DEFAULT 0,
  equipped_skin_id INT NOT NULL DEFAULT 0,
  banned TINYINT(1) NOT NULL DEFAULT 0,
  ban_reason VARCHAR(255) NULL,
  ban_until DATETIME NULL,
  KEY idx_player_account(account_id)
);

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(128) NOT NULL UNIQUE,
  description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS complaint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  content VARCHAR(1024) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  handler_id BIGINT NULL,
  handled_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_id BIGINT NULL,
  detail VARCHAR(512) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS item_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  kind INT NOT NULL,
  stack_limit INT NOT NULL DEFAULT 1,
  level_required INT NOT NULL DEFAULT 0,
  description VARCHAR(255) DEFAULT NULL,
  price INT NOT NULL DEFAULT 0,
  sell_price INT NOT NULL DEFAULT 0,
  effect_params VARCHAR(255) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS player_bag_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  item_config_id INT NOT NULL,
  count INT NOT NULL DEFAULT 1,
  bind INT NOT NULL DEFAULT 0,
  slot_index INT NOT NULL DEFAULT 0,
  equip_slot INT NOT NULL DEFAULT 0,
  affix_blob BLOB NULL COMMENT '装备随机词条 JSON',
  suit_id INT NOT NULL DEFAULT 0 COMMENT '套装ID预留（本轮不匹配）',
  KEY idx_player_slot (player_id, slot_index)
);

-- 命座预留：多角色 roster（本轮不挂 Buff）
CREATE TABLE IF NOT EXISTS player_characters (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  char_template_id INT NOT NULL COMMENT 'gacha 模板 ID，如 1001',
  constellation_lv TINYINT NOT NULL DEFAULT 0 COMMENT '0~6',
  starlight INT NOT NULL DEFAULT 0 COMMENT '满命后分解星辉累计',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_player_char (player_id, char_template_id),
  KEY idx_player_characters_player (player_id)
);

-- 钓鱼/烹饪预留表（本轮不联动玩法）
CREATE TABLE IF NOT EXISTS fishing_spot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  map_id INT NOT NULL,
  pos_x DOUBLE NOT NULL,
  pos_y DOUBLE NOT NULL,
  pos_z DOUBLE NOT NULL,
  fish_pool_json VARCHAR(512) NOT NULL DEFAULT '[]' COMMENT '鱼种池 JSON',
  KEY idx_fishing_spot_map (map_id)
);

CREATE TABLE IF NOT EXISTS cooking_recipe (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  input_item_ids VARCHAR(256) NOT NULL COMMENT '食材 itemId 列表 JSON',
  output_item_id INT NOT NULL,
  buff_json VARCHAR(256) DEFAULT NULL COMMENT '临时 Buff 配置预留'
);

-- 世界奇遇模板预留
CREATE TABLE IF NOT EXISTS world_incident_template (
  template_id INT PRIMARY KEY,
  type VARCHAR(32) NOT NULL COMMENT 'ESCORT/DEFEND/RACE',
  duration_sec INT NOT NULL DEFAULT 300,
  reward_plan VARCHAR(512) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS skill_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  effect VARCHAR(255) DEFAULT NULL,
  need_level INT NOT NULL DEFAULT 0,
  cooldown INT NOT NULL DEFAULT 0,
  mana_cost INT NOT NULL DEFAULT 0,
  cast_time DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  skill_type INT NOT NULL DEFAULT 1,
  target_type INT NOT NULL DEFAULT 1,
  skill_range INT NOT NULL DEFAULT 0,
  shape INT NOT NULL DEFAULT 1,
  shape_params JSON DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS player_skill (
  player_id BIGINT NOT NULL,
  skill_id INT NOT NULL,
  learn_time DATETIME(6) NOT NULL,
  PRIMARY KEY (player_id, skill_id)
);

CREATE TABLE IF NOT EXISTS buff_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  duration INT NOT NULL DEFAULT -1,
  periodic_interval INT DEFAULT NULL,
  stack_limit INT NOT NULL DEFAULT 1,
  effect_type INT NOT NULL,
  effect_params VARCHAR(255) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS map_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  default_lines INT NOT NULL DEFAULT 1,
  aoi_radius INT NOT NULL DEFAULT 300,
  grid_size INT NOT NULL DEFAULT 100,
  recommend_level INT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS monster_config (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  model_id INT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  hp_max INT NOT NULL DEFAULT 100,
  mp_max INT NOT NULL DEFAULT 0,
  attack INT NOT NULL DEFAULT 10,
  defense INT NOT NULL DEFAULT 5,
  exp_reward INT NOT NULL DEFAULT 0,
  description VARCHAR(255) DEFAULT NULL,
  map_id INT DEFAULT NULL,
  spawn_x FLOAT DEFAULT NULL,
  spawn_z FLOAT DEFAULT NULL,
  respawn_seconds INT DEFAULT 30,
  elite_flag INT DEFAULT 1,
  KEY idx_monster_map (map_id)
);

CREATE TABLE IF NOT EXISTS challenge_config (
  id INT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  challenge_type INT NOT NULL DEFAULT 1,
  wave_count INT NOT NULL DEFAULT 1,
  waves_json TEXT NOT NULL,
  star_thresholds_json VARCHAR(255) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS activity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type INT NOT NULL,
  opened TINYINT(1) NOT NULL DEFAULT 0,
  data TEXT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_type (type)
);

CREATE TABLE IF NOT EXISTS chat_message_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  channel INT NOT NULL,
  target_id BIGINT NULL,
  msg_type INT NOT NULL,
  content VARCHAR(512) NOT NULL,
  server_ts BIGINT NOT NULL,
  KEY idx_chat_sender (sender_id),
  KEY idx_chat_ts (server_ts)
);

CREATE TABLE IF NOT EXISTS player_friend (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  friend_id BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_player_friend (player_id, friend_id),
  KEY idx_friend_player (player_id)
);

CREATE TABLE IF NOT EXISTS player_mail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  body VARCHAR(1024) NOT NULL,
  claimed TINYINT(1) NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  gold INT NOT NULL DEFAULT 0,
  attachments_json VARCHAR(1024) DEFAULT NULL,
  KEY idx_mail_player (player_id)
);

CREATE TABLE IF NOT EXISTS player_quest_progress (
  player_id BIGINT NOT NULL,
  quest_id INT NOT NULL,
  status INT NOT NULL DEFAULT 0,
  progress INT NOT NULL DEFAULT 0,
  PRIMARY KEY (player_id, quest_id)
);

CREATE TABLE IF NOT EXISTS player_skin_owned (
  player_id BIGINT NOT NULL,
  skin_id INT NOT NULL,
  obtained_at BIGINT NOT NULL,
  PRIMARY KEY (player_id, skin_id)
);

-- P0：货币流水账本
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

-- P0：道具流水账本
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

-- P0：发奖幂等表（替代纯 Redis SETNX）
CREATE TABLE IF NOT EXISTS grant_idempotency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  idempotency_key VARCHAR(191) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DONE',
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_grant_idem (player_id, idempotency_key),
  KEY idx_grant_created (created_at)
);

-- P0：商城订单持久化（Redis 仍作热缓存）
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

-- P0：MQ Outbox（支付发货等可靠投递）
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

-- P0：MQ Inbox（单体模式下与 activity 同库；拆库时 activity_db 也有同表）
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

CREATE TABLE IF NOT EXISTS login_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  account_name VARCHAR(64) NULL,
  client_ip VARCHAR(64) NULL,
  device_id VARCHAR(128) NULL,
  client_type VARCHAR(32) NULL,
  user_agent VARCHAR(256) NULL,
  success TINYINT(1) NOT NULL DEFAULT 0,
  risk_flag VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_login_history_account_time (account_id, created_at)
);

CREATE TABLE IF NOT EXISTS session_backup (
  player_id BIGINT PRIMARY KEY,
  account_id BIGINT NULL,
  session_id VARCHAR(128) NULL,
  node_id VARCHAR(64) NULL,
  scene_id INT NULL,
  device_id VARCHAR(128) NULL,
  client_type VARCHAR(32) NULL,
  client_ip VARCHAR(64) NULL,
  login_time BIGINT NULL,
  last_heartbeat BIGINT NULL,
  expire_at BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_session_backup_expire (expire_at),
  KEY idx_session_backup_account (account_id)
);

-- 已有库升级（按需执行）：
-- ALTER TABLE player ADD COLUMN equipped_skin_id INT NOT NULL DEFAULT 0;
