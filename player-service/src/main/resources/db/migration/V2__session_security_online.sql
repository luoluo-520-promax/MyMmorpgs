-- 会话/风控/封禁：login_history + session_backup + account/player ban 字段
ALTER TABLE account ADD COLUMN banned TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE account ADD COLUMN ban_reason VARCHAR(255) NULL;
ALTER TABLE account ADD COLUMN ban_until DATETIME NULL;

ALTER TABLE player ADD COLUMN banned TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE player ADD COLUMN ban_reason VARCHAR(255) NULL;
ALTER TABLE player ADD COLUMN ban_until DATETIME NULL;

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
