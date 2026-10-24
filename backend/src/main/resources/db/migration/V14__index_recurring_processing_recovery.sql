CREATE INDEX idx_recurring_rule_runs_status_processed
  ON recurring_rule_runs (status, processed_at);
