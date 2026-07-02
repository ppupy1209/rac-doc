CREATE TABLE document (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(255) NOT NULL,
  source      VARCHAR(255),
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE chunk (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  document_id  BIGINT NOT NULL,
  seq          INT NOT NULL,
  content      TEXT NOT NULL,
  embedding    JSON NOT NULL,           -- float 배열 (nomic-embed-text = 768차원)
  token_count  INT NOT NULL,
  created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
  INDEX idx_chunk_document (document_id)
);
