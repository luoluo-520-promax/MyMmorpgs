-- 将旧版 player_db 结构迁移到与 JPA 实体一致（可重复执行，已存在列/表会跳过或报错可忽略）

USE player_db;

-- account: username -> account_name, created_at -> create_time
ALTER TABLE account CHANGE COLUMN username account_name VARCHAR(64) NOT NULL;
ALTER TABLE account CHANGE COLUMN created_at create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE account ADD COLUMN last_login_time DATETIME NULL;
ALTER TABLE account MODIFY COLUMN password VARCHAR(128) NOT NULL;

-- player: 补齐 vip_right / gold，level/exp 设为非空
ALTER TABLE player ADD COLUMN vip_right INT NOT NULL DEFAULT 0;
ALTER TABLE player ADD COLUMN gold BIGINT NOT NULL DEFAULT 0;
ALTER TABLE player MODIFY COLUMN level INT NOT NULL DEFAULT 1;
ALTER TABLE player MODIFY COLUMN exp BIGINT NOT NULL DEFAULT 0;
