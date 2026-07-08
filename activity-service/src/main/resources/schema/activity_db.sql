-- activity-service 独立库初始化脚本
-- 建议在 MySQL 中先创建数据库：CREATE DATABASE IF NOT EXISTS activity_db DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS activity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_type INT NOT NULL,
  title VARCHAR(128) NOT NULL,
  status INT NOT NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_activity_progress (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  progress_value BIGINT DEFAULT 0,
  reward_claimed TINYINT DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_player_activity(player_id, activity_id)
);

