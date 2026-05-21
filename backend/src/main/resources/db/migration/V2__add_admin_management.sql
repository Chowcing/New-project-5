ALTER TABLE users
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER nickname,
  ADD INDEX idx_users_status (status);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_audit_logs_created_at (created_at),
  INDEX idx_admin_audit_logs_admin_user_id (admin_user_id),
  INDEX idx_admin_audit_logs_target (target_type, target_id),
  CONSTRAINT fk_admin_audit_logs_admin_user FOREIGN KEY (admin_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
