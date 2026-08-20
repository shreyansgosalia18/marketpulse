-- Durable storage for TrendStore, replacing its previous in-memory maps.
-- Composite primary keys are the idempotency mechanism: an
-- INSERT ... ON CONFLICT ... DO UPDATE on these keys is what makes
-- redelivering the same Kafka message safe to reprocess.

CREATE TABLE price_bars (
    ticker      VARCHAR(20)     NOT NULL,
    trade_date  DATE            NOT NULL,
    open        NUMERIC(19, 4)  NOT NULL,
    high        NUMERIC(19, 4)  NOT NULL,
    low         NUMERIC(19, 4)  NOT NULL,
    close       NUMERIC(19, 4)  NOT NULL,
    volume      BIGINT          NOT NULL,
    PRIMARY KEY (ticker, trade_date)
);

CREATE TABLE sentiment_scores (
    ticker          VARCHAR(20)     NOT NULL,
    article_uuid    VARCHAR(64)     NOT NULL,
    label           VARCHAR(16)     NOT NULL,
    compound_score  NUMERIC(5, 4)   NOT NULL,
    scored_at       TIMESTAMPTZ     NOT NULL,
    PRIMARY KEY (ticker, article_uuid)
);
