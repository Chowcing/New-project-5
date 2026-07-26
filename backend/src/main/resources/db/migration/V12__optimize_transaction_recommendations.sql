CREATE INDEX idx_transactions_user_recommendation
  ON transactions (user_id, deleted, type, occurred_at, id);
