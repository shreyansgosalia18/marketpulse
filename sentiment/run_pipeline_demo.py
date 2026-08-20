"""Consume marketpulse.news.raw, score sentiment, publish marketpulse.sentiment.raw.

Usage:
    python run_pipeline_demo.py
Requires the local Docker Compose Kafka broker to be running (see
docs/reference/local-dev.md). Processes whatever's currently on the topic,
then exits - see docs/reference/sentiment-pipeline.md for why this isn't a
long-running daemon.
"""

from marketpulse_sentiment.pipeline import SentimentPipeline


def main() -> None:
    pipeline = SentimentPipeline()
    try:
        result = pipeline.run_once()
    finally:
        pipeline.close()

    print(f"consumed={result.consumed} scored={result.scored} published={result.published}")
    for error in result.errors:
        print(f"  ERROR: {error}")


if __name__ == "__main__":
    main()
