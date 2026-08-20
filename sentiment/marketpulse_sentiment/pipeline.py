import json
from dataclasses import dataclass, field
from datetime import datetime, timezone

from kafka import KafkaConsumer, KafkaProducer
from kafka.errors import KafkaError

from .events import NEWS_TOPIC, SENTIMENT_TOPIC, parse_news_event, sentiment_event_to_message
from .models import SentimentEvent
from .scorer import score_sentiment

# 127.0.0.1, not "localhost", and an explicit api_version - see
# marketpulse/scraper's kafka_producer.py and docs/reference/event-stream.md
# for why both are necessary on this stack.
DEFAULT_BOOTSTRAP_SERVERS = "127.0.0.1:9092"
DEFAULT_API_VERSION = (3, 8)
DEFAULT_CONSUMER_GROUP = "marketpulse-sentiment-pipeline"


@dataclass
class PipelineResult:
    """Outcome of one run_once() pass."""

    consumed: int = 0
    scored: int = 0
    published: int = 0
    errors: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


class SentimentPipeline:
    """Consumes marketpulse.news.raw, scores each article's title, and
    publishes results to marketpulse.sentiment.raw.

    Runs in bounded batches - consumes whatever's currently available, then
    returns - rather than as a long-running daemon (see the user story for
    why). A malformed message or a publish failure for one item is recorded
    in the result and does not stop the rest of the batch.
    """

    def __init__(
        self,
        bootstrap_servers: str = DEFAULT_BOOTSTRAP_SERVERS,
        api_version: tuple = DEFAULT_API_VERSION,
        consumer_group: str = DEFAULT_CONSUMER_GROUP,
        auto_offset_reset: str = "earliest",
        consumer_timeout_ms: int = 5000,
    ):
        self._consumer = KafkaConsumer(
            NEWS_TOPIC,
            bootstrap_servers=bootstrap_servers,
            api_version=api_version,
            group_id=consumer_group,
            auto_offset_reset=auto_offset_reset,
            enable_auto_commit=True,
            consumer_timeout_ms=consumer_timeout_ms,
            value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        )
        self._producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            api_version=api_version,
            key_serializer=lambda k: k.encode("utf-8"),
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )

    def run_once(self) -> PipelineResult:
        result = PipelineResult()
        for message in self._consumer:
            result.consumed += 1
            self._process_one(message.value, result)
        self._producer.flush()
        return result

    def _process_one(self, payload: dict, result: PipelineResult) -> None:
        try:
            news_event = parse_news_event(payload)
        except ValueError as exc:
            result.errors.append(str(exc))
            return

        score = score_sentiment(news_event.title)
        result.scored += 1

        sentiment_event = SentimentEvent(
            ticker=news_event.ticker,
            article_uuid=news_event.article_uuid,
            label=score.label,
            compound_score=score.compound_score,
            scored_at=datetime.now(timezone.utc),
        )

        try:
            future = self._producer.send(
                SENTIMENT_TOPIC,
                key=sentiment_event.ticker,
                value=sentiment_event_to_message(sentiment_event),
            )
            future.get(timeout=10)
        except KafkaError as exc:
            result.errors.append(f"{sentiment_event.ticker}: {exc}")
        else:
            result.published += 1

    def close(self) -> None:
        self._consumer.close()
        self._producer.close()
