# User Story: Sentiment Scoring Pipeline

**Component:** Sentiment Pipeline (new — first story for this component) · **Architecture:** [sentiment pipeline internals](../architecture/sentiment-pipeline.md) · **Consumes:** [kafka-event-producers](kafka-event-producers.md)'s `marketpulse.news.raw`

```
As a MarketPulse operator
I want each scraped news article scored for sentiment per ticker
So that sentiment trends can later be correlated with price movement
```

## Design decision: no code dependency on the scraper

This component consumes `marketpulse.news.raw` as a **Kafka consumer**, not by importing anything from the `scraper/` package. The whole point of the Event Stream (per the [root README](../../README.md#architecture): "decouples ingestion from processing") is that consumers only need to agree on the documented JSON schema, not share code with the producer. This is deliberately tested: the integration test seeds a news event with a raw `KafkaProducer`, not the scraper's own producer.

## Scope decisions

- **Scoring input is the article title only.** `NewsArticle`/the `marketpulse.news.raw` schema has no body text (see [scraper-news-relevance-filtering](scraper-news-relevance-filtering.md) — the source doesn't reliably provide it). This is headline sentiment, not full-article sentiment.
- **Lexicon-based scoring (VADER), not a trained ML model.** Deterministic, fast, no model download, well-suited to short text. Extended with a small finance-specific lexicon supplement (e.g. "beats", "misses", "bullish", "bearish", "plunge", "rally") since generic VADER doesn't weight financial jargon. This is not a claim of state-of-the-art accuracy — see [Known limitations](../reference/sentiment-pipeline.md#known-limitations--not-yet-built).
- **Bounded batch runs, not a long-running daemon.** Matches the pattern already used for the Kafka producer CLI — `SentimentPipeline` consumes whatever's currently available, scores it, publishes results, and returns a summary. Turning this into a continuously-running service is a deployment concern for later, not part of this story.
- **Both a 3-way label and the raw compound score are published** (`positive`/`negative`/`neutral` plus a `-1..1` continuous score) — the label is easy to read, but trend correlation (the whole point of this pipeline per the README) needs the continuous value, not just a bucket.

## Acceptance criteria

- Given a `marketpulse.news.raw` message (matching the documented schema), when consumed, then it's scored and a `marketpulse.sentiment.raw` message is published with the ticker, the source article's `uuid`, a sentiment label, and a compound score.
- Given article text that's clearly positive/negative (e.g. "record profits" vs. "shares plunge"), when scored, then the label matches the expected polarity — not asserting an exact score, since VADER's exact values aren't a stable contract.
- Given article text containing finance-specific sentiment words the base VADER lexicon doesn't weight correctly (e.g. "beats estimates", "bearish outlook"), when scored, then the finance lexicon extension correctly shifts the score in the expected direction (verified by comparing against unextended VADER).
- Given neutral, factual text with no clear sentiment, when scored, then the label is `neutral`.
- Given a consumed message that doesn't match the documented schema (missing required fields), when processed, then it's skipped and recorded as an error — it does not crash the batch or get published as a malformed sentiment event.
- Given a batch of multiple news messages, when processed, then one message's scoring/publish failure doesn't stop the rest of the batch — same per-item isolation principle used throughout this codebase.
- Given a message is published to `marketpulse.sentiment.raw`, when consumed by a real client, then it deserializes correctly — verified against a real broker, not just that `.send()` didn't raise.

## Explicitly out of scope

- No long-running service/daemon mode, no restart-on-crash, no scheduling — a deployment concern for later.
- No full-article sentiment (no body text available at all in the current schema).
- No custom-trained or transformer-based model (e.g. FinBERT) — a possible future upgrade if lexicon-based accuracy proves insufficient once real trend correlation is being evaluated.

## Test coverage

| Acceptance criterion | Test(s) |
|---|---|
| News event consumed → sentiment event published with ticker, article uuid, label, score | `test_scores_and_publishes_valid_news_event`, `test_published_sentiment_event_is_consumable_from_real_broker` |
| Clearly positive/negative text → correct label | `test_positive_text_scores_positive`, `test_negative_text_scores_negative` |
| Finance-specific words shift score in the expected direction | `test_finance_lexicon_shifts_beats_estimates_positive`, `test_finance_lexicon_shifts_bearish_negative` |
| Neutral factual text → neutral label | `test_neutral_text_scores_neutral` |
| Malformed message → skipped, recorded as error, not published | `test_skips_message_with_missing_required_fields` |
| One message's failure doesn't stop the batch | `test_one_failure_does_not_stop_the_batch` |
| Real broker round-trip | `test_published_sentiment_event_is_consumable_from_real_broker` |

## Status

Implemented and tested — see [reference/sentiment-pipeline.md](../reference/sentiment-pipeline.md) for usage.
