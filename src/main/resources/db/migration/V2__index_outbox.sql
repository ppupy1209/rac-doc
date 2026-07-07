CREATE TABLE index_outbox (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type    VARCHAR(32)  NOT NULL,
  chunk_id      BIGINT       NOT NULL,
  document_id   BIGINT       NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at  DATETIME(6),
  INDEX idx_outbox_status (status, id)
);
