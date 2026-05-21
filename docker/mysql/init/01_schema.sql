CREATE DATABASE IF NOT EXISTS expense_tracker
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE expense_tracker;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_refresh_tokens_user_id (user_id),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL,
  icon VARCHAR(32) NULL,
  color VARCHAR(16) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_name VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN name ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_categories_user_type_active_name (user_id, type, active_name),
  INDEX idx_categories_user_type (user_id, type, deleted),
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_methods (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  icon VARCHAR(32) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_name VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN name ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_methods_user_active_name (user_id, active_name),
  INDEX idx_payment_methods_user_id (user_id, deleted),
  CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  type VARCHAR(16) NOT NULL,
  item_name VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  occurred_at DATETIME NOT NULL,
  channel VARCHAR(16) NOT NULL,
  online_app VARCHAR(64) NULL,
  offline_place VARCHAR(128) NULL,
  payment_method_id BIGINT NOT NULL,
  payment_method_name VARCHAR(64) NOT NULL,
  category_id BIGINT NOT NULL,
  note VARCHAR(255) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_transactions_user_time (user_id, deleted, occurred_at),
  INDEX idx_transactions_user_type (user_id, type),
  INDEX idx_transactions_user_channel (user_id, channel),
  INDEX idx_transactions_payment_method (payment_method_id),
  CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT fk_transactions_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS budgets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  month CHAR(7) NOT NULL,
  category_id BIGINT NULL,
  amount DECIMAL(12,2) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_category_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN COALESCE(category_id, 0) ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_budgets_user_month_active_category (user_id, month, active_category_id),
  INDEX idx_budgets_user_month (user_id, month, deleted),
  CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recurring_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL,
  item_name VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  online_app VARCHAR(64) NULL,
  offline_place VARCHAR(128) NULL,
  payment_method_id BIGINT NOT NULL,
  payment_method_name VARCHAR(64) NOT NULL,
  category_id BIGINT NOT NULL,
  category_name VARCHAR(64) NOT NULL,
  note VARCHAR(255) NULL,
  schedule_type VARCHAR(16) NOT NULL,
  interval_value INT NOT NULL DEFAULT 1,
  day_of_month INT NULL,
  weekday VARCHAR(16) NULL,
  start_date DATE NOT NULL,
  next_run_date DATE NULL,
  end_date DATE NULL,
  reminder_days_before INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_name VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN name ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recurring_rules_user_active_name (user_id, active_name),
  INDEX idx_recurring_rules_user_status_next (user_id, status, next_run_date, deleted),
  INDEX idx_recurring_rules_user_schedule (user_id, schedule_type, deleted),
  CONSTRAINT fk_recurring_rules_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_recurring_rules_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT fk_recurring_rules_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recurring_rule_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  rule_id BIGINT NOT NULL,
  rule_name VARCHAR(64) NOT NULL,
  due_date DATE NOT NULL,
  reminder_days_before INT NOT NULL DEFAULT 0,
  type VARCHAR(16) NOT NULL,
  item_name VARCHAR(64) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  online_app VARCHAR(64) NULL,
  offline_place VARCHAR(128) NULL,
  payment_method_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  note VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL,
  transaction_id BIGINT NULL,
  error_message VARCHAR(255) NULL,
  processed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recurring_rule_runs_rule_due_date (rule_id, due_date),
  INDEX idx_recurring_rule_runs_user_status_due (user_id, status, due_date),
  INDEX idx_recurring_rule_runs_user_rule (user_id, rule_id, due_date),
  INDEX idx_recurring_rule_runs_transaction (transaction_id),
  CONSTRAINT fk_recurring_rule_runs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_recurring_rule_runs_rule FOREIGN KEY (rule_id) REFERENCES recurring_rules(id) ON DELETE CASCADE,
  CONSTRAINT fk_recurring_rule_runs_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT fk_recurring_rule_runs_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id),
  CONSTRAINT fk_recurring_rule_runs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS import_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  original_filename VARCHAR(255) NULL,
  content_hash CHAR(64) NOT NULL,
  csv_content MEDIUMTEXT NULL,
  status VARCHAR(16) NOT NULL,
  total_rows INT NOT NULL DEFAULT 0,
  imported_rows INT NOT NULL DEFAULT 0,
  failed_rows INT NOT NULL DEFAULT 0,
  result_json MEDIUMTEXT NULL,
  error_message VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_import_jobs_user_status (user_id, status, created_at),
  INDEX idx_import_jobs_user_hash_status (user_id, content_hash, status),
  CONSTRAINT fk_import_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
