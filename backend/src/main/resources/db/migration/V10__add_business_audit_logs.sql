CREATE TABLE IF NOT EXISTS business_audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NULL,
  source VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  request_id VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_business_audit_logs_created_at (created_at),
  INDEX idx_business_audit_logs_user_created_at (user_id, created_at),
  INDEX idx_business_audit_logs_action_created_at (action, created_at),
  INDEX idx_business_audit_logs_target (target_type, target_id),
  INDEX idx_business_audit_logs_request_id (request_id),
  CONSTRAINT fk_business_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
