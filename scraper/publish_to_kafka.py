"""Fetch a watchlist's price and news data and publish it onto Kafka.

Usage:
    python publish_to_kafka.py AAPL MSFT
Defaults to AAPL and MSFT if no tickers are given. Requires the local
Docker Compose Kafka broker to be running (see docs/reference/local-dev.md).
"""

import sys

from marketpulse_scraper.kafka_producer import MarketPulseProducer
from marketpulse_scraper.news_scraper import NewsScraper
from marketpulse_scraper.watchlist import WatchlistScraper


def main() -> None:
    tickers = sys.argv[1:] or ["AAPL", "MSFT"]

    price_results = WatchlistScraper(tickers).fetch_history()
    news_results = NewsScraper(tickers).fetch_news()

    producer = MarketPulseProducer()
    try:
        price_outcome = producer.publish_price_results(price_results)
        news_outcome = producer.publish_news_results(news_results)
        producer.flush()
    finally:
        producer.close()

    print(f"{price_outcome.topic}: published {price_outcome.published} messages")
    for error in price_outcome.errors:
        print(f"  ERROR: {error}")

    print(f"{news_outcome.topic}: published {news_outcome.published} messages")
    for error in news_outcome.errors:
        print(f"  ERROR: {error}")


if __name__ == "__main__":
    main()
