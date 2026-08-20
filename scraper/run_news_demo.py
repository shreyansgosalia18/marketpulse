"""Manually fetch and print news headlines for a watchlist.

Usage:
    python run_news_demo.py AAPL MSFT
Defaults to AAPL and MSFT if no tickers are given.
"""

import sys

from marketpulse_scraper.news_scraper import NewsScraper


def main() -> None:
    tickers = sys.argv[1:] or ["AAPL", "MSFT"]
    scraper = NewsScraper(tickers)

    for result in scraper.fetch_news():
        if not result.ok:
            print(f"{result.ticker}: ERROR - {result.error}")
            continue
        print(f"{result.ticker}: {len(result.articles)} articles")
        for article in result.articles[:3]:
            print(f"  - [{article.published_at:%Y-%m-%d}] {article.title} ({article.publisher})")


if __name__ == "__main__":
    main()
