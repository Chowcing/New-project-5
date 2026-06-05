ALTER TABLE users
  ADD COLUMN email VARCHAR(254) NULL AFTER nickname,
  ADD COLUMN email_verified_at DATETIME NULL AFTER email,
  ADD UNIQUE KEY uk_users_email (email);

CREATE TABLE IF NOT EXISTS auth_challenges (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  challenge_id VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NULL,
  email VARCHAR(254) NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash CHAR(64) NULL,
  expires_at DATETIME NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  consumed_at DATETIME NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_auth_challenges_challenge_id (challenge_id),
  INDEX idx_auth_challenges_email_purpose (email, purpose, consumed_at, sent_at),
  INDEX idx_auth_challenges_user_purpose (user_id, purpose, consumed_at),
  CONSTRAINT fk_auth_challenges_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
