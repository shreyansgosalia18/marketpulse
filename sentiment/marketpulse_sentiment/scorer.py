from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

from .models import NEGATIVE, NEUTRAL, POSITIVE, SentimentScore

# VADER's base lexicon is tuned for general/social-media text and doesn't
# weight common financial jargon correctly. This is a deliberately small,
# hand-picked supplement, not a comprehensive finance lexicon - see
# docs/reference/sentiment-pipeline.md's Known Limitations.
_FINANCE_LEXICON = {
    "beats": 2.0,
    "misses": -2.0,
    "bullish": 2.5,
    "bearish": -2.5,
    "plunge": -2.5,
    "plunges": -2.5,
    "soar": 2.5,
    "soars": 2.5,
    "rally": 2.0,
    "rallies": 2.0,
    "slump": -2.0,
    "slumps": -2.0,
    "surge": 2.0,
    "surges": 2.0,
    "tumble": -2.0,
    "tumbles": -2.0,
    "downgrade": -2.0,
    "downgrades": -2.0,
    "upgrade": 2.0,
    "upgrades": 2.0,
    "outperform": 2.0,
    "underperform": -2.0,
    "layoffs": -2.5,
}

_POSITIVE_THRESHOLD = 0.05
_NEGATIVE_THRESHOLD = -0.05

_analyzer = SentimentIntensityAnalyzer()
_analyzer.lexicon.update(_FINANCE_LEXICON)


def score_sentiment(text: str) -> SentimentScore:
    """Score a piece of text (e.g. a news headline) for sentiment."""
    compound = _analyzer.polarity_scores(text)["compound"]
    if compound >= _POSITIVE_THRESHOLD:
        label = POSITIVE
    elif compound <= _NEGATIVE_THRESHOLD:
        label = NEGATIVE
    else:
        label = NEUTRAL
    return SentimentScore(label=label, compound_score=compound)
