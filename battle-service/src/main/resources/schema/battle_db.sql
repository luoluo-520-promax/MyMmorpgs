-- battle-service 独立库初始化脚本
-- 建议在 MySQL 中先创建数据库：CREATE DATABASE IF NOT EXISTS battle_db DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS battle_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  battle_id BIGINT NOT NULL UNIQUE,
  player_id BIGINT NOT NULL,
  scene_id INT NOT NULL,
  enemy_entity_id BIGINT NOT NULL,
  result INT NOT NULL,
  exp_reward INT DEFAULT 0,
  duration_sec INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_battle_player(player_id),
  KEY idx_battle_scene(scene_id)
);

