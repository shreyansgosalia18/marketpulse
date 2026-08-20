package com.marketpulse.aggregation.persistence;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.marketpulse.aggregation.trend.SentimentRecord;

/** Plain JDBC - see docs/user-stories/postgres-persistence-layer.md for why. */
@Repository
public class SentimentScoreRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO sentiment_scores (ticker, article_uuid, label, compound_score, scored_at)
            VALUES (:ticker, :articleUuid, :label, :compoundScore, :scoredAt)
            ON CONFLICT (ticker, article_uuid)
            DO UPDATE SET label = EXCLUDED.label, compound_score = EXCLUDED.compound_score,
                          scored_at = EXCLUDED.scored_at
            """;

    private static final String FIND_BY_TICKER_SQL = """
            SELECT ticker, article_uuid, label, compound_score, scored_at
            FROM sentiment_scores
            WHERE ticker = :ticker
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SentimentScoreRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(SentimentRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ticker", record.ticker())
                .addValue("articleUuid", record.articleUuid())
                .addValue("label", record.label())
                .addValue("compoundScore", record.compoundScore())
                .addValue("scoredAt", Timestamp.from(record.scoredAt()));
        jdbcTemplate.update(UPSERT_SQL, params);
    }

    public List<SentimentRecord> findByTicker(String ticker) {
        return jdbcTemplate.query(
                FIND_BY_TICKER_SQL,
                new MapSqlParameterSource("ticker", ticker),
                (rs, rowNum) -> new SentimentRecord(
                        rs.getString("ticker"),
                        rs.getString("article_uuid"),
                        rs.getString("label"),
                        rs.getDouble("compound_score"),
                        rs.getTimestamp("scored_at").toInstant()));
    }
}
