-- 已有库增量：邮件附件道具 JSON（列已存在时请跳过）
ALTER TABLE player_mail
  ADD COLUMN attachments_json VARCHAR(1024) DEFAULT NULL;
