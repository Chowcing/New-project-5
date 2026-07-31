ALTER TABLE users
  ADD COLUMN trash_retention_days SMALLINT NOT NULL DEFAULT 30 AFTER status,
  ADD CONSTRAINT chk_users_trash_retention_days
    CHECK (trash_retention_days BETWEEN 1 AND 365);

ALTER TABLE transactions
  ADD COLUMN trashed_at DATETIME NULL AFTER note,
  ADD INDEX idx_transactions_user_trash_time
    (user_id, deleted, trashed_at, id);
