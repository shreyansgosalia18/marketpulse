package com.marketpulse.aggregation.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.marketpulse.aggregation.trend.PriceBarRecord;

/**
 * Plain JDBC, not JPA - see docs/user-stories/postgres-persistence-layer.md
 * for why. The upsert's ON CONFLICT clause on the (ticker, trade_date)
 * primary key is what makes recording the same bar twice idempotent.
 */
@Repository
public class PriceBarRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO price_bars (ticker, trade_date, open, high, low, close, volume)
            VALUES (:ticker, :tradeDate, :open, :high, :low, :close, :volume)
            ON CONFLICT (ticker, trade_date)
            DO UPDATE SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low,
                          close = EXCLUDED.close, volume = EXCLUDED.volume
            """;

    private static final String FIND_BY_TICKER_SQL = """
            SELECT ticker, trade_date, open, high, low, close, volume
            FROM price_bars
            WHERE ticker = :ticker
            ORDER BY trade_date
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceBarRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(PriceBarRecord bar) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ticker", bar.ticker())
                .addValue("tradeDate", bar.tradeDate())
                .addValue("open", bar.open())
                .addValue("high", bar.high())
                .addValue("low", bar.low())
                .addValue("close", bar.close())
                .addValue("volume", bar.volume());
        jdbcTemplate.update(UPSERT_SQL, params);
    }

    public List<PriceBarRecord> findByTicker(String ticker) {
        return jdbcTemplate.query(
                FIND_BY_TICKER_SQL,
                new MapSqlParameterSource("ticker", ticker),
                (rs, rowNum) -> new PriceBarRecord(
                        rs.getString("ticker"),
                        rs.getObject("trade_date", LocalDate.class),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("volume")));
    }
}
