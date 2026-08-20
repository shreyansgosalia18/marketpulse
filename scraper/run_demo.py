"""Manually fetch and print price history for a watchlist.

Usage:
    python run_demo.py AAPL.US MSFT.US
Defaults to AAPL.US and MSFT.US if no tickers are given.
"""

import sys

from marketpulse_scraper.watchlist import WatchlistScraper


def main() -> None:
    tickers = sys.argv[1:] or ["AAPL.US", "MSFT.US"]
    scraper = WatchlistScraper(tickers)

    for result in scraper.fetch_history():
        if result.ok:
            latest = result.bars[-1]
            print(
                f"{result.ticker}: {len(result.bars)} bars, "
                f"latest {latest.trade_date} close={latest.close} volume={latest.volume}"
            )
        else:
            print(f"{result.ticker}: ERROR - {result.error}")


if __name__ == "__main__":
    main()
