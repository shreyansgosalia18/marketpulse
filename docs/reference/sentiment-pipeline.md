# Reference: Sentiment Pipeline

**User story:** [sentiment-scoring-pipeline](../user-stories/sentiment-scoring-pipeline.md) · **Architecture:** [sentiment pipeline internals](../architecture/sentiment-pipeline.md)

Consumes `marketpulse.news.raw`, scores each article's title for sentiment, and publishes to `marketpulse.sentiment.raw`.

> **Status:** implemented and tested, verified against a real broker. Runs as a bounded batch job (`run_once()`), not a long-running service — see [architecture](../architecture/sentiment-pipeline.md#why-bounded-batches-not-a-daemon).

## Schema

```json
// marketpulse.sentiment.raw
{
  "schema_version": 1,
  "event_type": "sentiment_score",
  "ticker": "AAPL",
  "article_uuid": "abc-123",
  "sentiment": "positive",
  "compound_score": 0.65,
  "scored_at": "2024-01-02T12:05:00+00:00"
}
```

`sentiment` is one of `positive` / `negative` / `neutral` (VADER's standard thresholds: compound ≥ 0.05 → positive, ≤ -0.05 → negative, else neutral). `compound_score` is the raw `-1..1` value — kept alongside the label because trend correlation (the pipeline's actual purpose per the [root README](../../README.md)) needs a continuous signal, not just a three-way bucket.

## Usage

```python
from marketpulse_sentiment.pipeline import SentimentPipeline

pipeline = SentimentPipeline()
try:
    result = pipeline.run_once()
finally:
    pipeline.close()

print(result.consumed, result.scored, result.published, result.errors)
```

CLI, for manual/ad-hoc runs (requires the [local Kafka stack](local-dev.md) running):

```
cd sentiment
.venv\Scripts\python run_pipeline_demo.py
```

## Setup

```
cd sentiment
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements-dev.txt
```

## Testing

```
cd sentiment
.venv\Scripts\python -m pytest tests/ -v
```

12 tests: `test_scorer.py` (sentiment scoring, including two tests that directly compare against unextended VADER to prove the finance lexicon actually shifts the score, not just that the sentence happened to score correctly anyway), `test_events.py` (schema parse/serialize), `test_pipeline.py` (mocked Kafka — skip/isolation behavior), and `test_pipeline_integration.py` (**live**, against a real broker — seeds `marketpulse.news.raw` with a raw `KafkaProducer`, deliberately not this project's own producer, then verifies a real `marketpulse.sentiment.raw` message comes out the other end). The integration test auto-skips if Kafka isn't reachable. Also independently verified with `kafka-console-consumer.sh` — not just this project's own test code.

## Known limitations / not yet built

- **Headline-only sentiment.** `marketpulse.news.raw` has no article body text (see [scraper-news-relevance-filtering](../user-stories/scraper-news-relevance-filtering.md)), so this scores titles only — the same limitation any headline-based sentiment system has.
- **Lexicon-based, not a trained model.** VADER + a ~20-word finance supplement is fast and deterministic, but it's not a claim of state-of-the-art financial sentiment accuracy. A transformer-based model (e.g. FinBERT) is a plausible future upgrade if lexicon accuracy proves insufficient once real trend correlation is being evaluated against it — not attempted now since there's nothing yet to validate accuracy against.
- **The finance lexicon is small and hand-picked** (~20 words: beats/misses, bullish/bearish, plunge/soar, rally/slump, surge/tumble, upgrade/downgrade, outperform/underperform, layoffs) — not a comprehensive financial sentiment lexicon. Easy to extend (`scorer.py`'s `_FINANCE_LEXICON` dict) as gaps are found.
- **No long-running service mode** — see [architecture](../architecture/sentiment-pipeline.md#why-bounded-batches-not-a-daemon).
- **At-least-once, not exactly-once.** The consumer group auto-commits after processing; a crash between publishing a sentiment event and committing the offset would reprocess that message on the next run, producing a duplicate sentiment event for the same article. Not a problem yet since nothing downstream consumes these events; revisit once the Aggregation Service exists.

## Assumptions made

- **Component is fully decoupled from the scraper** — no shared code, only the documented Kafka schema. Deliberately tested by seeding the integration test's input message with a plain `KafkaProducer`, not the scraper's own producer.
- **Consumer group rebalancing needs an explicit wait, not a single poll, before you can trust "latest" offset positioning.** The integration test initially failed intermittently — `pipeline.run_once()` returned 0 consumed messages even though the seed message was published after a single `poll()` call. A brand-new consumer group's join/sync rebalance can take longer than one short poll to actually complete and get "latest" applied; publishing before that finishes means the message arrives before the consumer is positioned to see it. Fixed by polling in a loop until `consumer.assignment()` is non-empty before publishing anything.
- Same `kafka-python==2.3.2` pin, explicit `api_version=(3, 8)`, and `127.0.0.1` (not `localhost`) as the scraper's Kafka producer — see [docs/reference/event-stream.md](event-stream.md) for why.
