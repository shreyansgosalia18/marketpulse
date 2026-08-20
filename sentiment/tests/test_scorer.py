from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer

from marketpulse_sentiment.models import NEGATIVE, NEUTRAL, POSITIVE
from marketpulse_sentiment.scorer import score_sentiment


def test_positive_text_scores_positive():
    result = score_sentiment("Company reports record profits and strong growth")

    assert result.label == POSITIVE
    assert result.compound_score > 0


def test_negative_text_scores_negative():
    result = score_sentiment("Shares plunge after disappointing earnings")

    assert result.label == NEGATIVE
    assert result.compound_score < 0


def test_neutral_text_scores_neutral():
    result = score_sentiment("The company will report quarterly earnings on Thursday")

    assert result.label == NEUTRAL


def test_finance_lexicon_shifts_beats_estimates_positive():
    text = "Company beats estimates for the quarter"
    baseline = SentimentIntensityAnalyzer().polarity_scores(text)["compound"]

    result = score_sentiment(text)

    # Proves the finance lexicon extension actually moved the score, not
    # just that the sentence happened to already score positive.
    assert result.compound_score > baseline
    assert result.label == POSITIVE


def test_finance_lexicon_shifts_bearish_negative():
    text = "Analysts turn bearish on the stock outlook"
    baseline = SentimentIntensityAnalyzer().polarity_scores(text)["compound"]

    result = score_sentiment(text)

    assert result.compound_score < baseline
    assert result.label == NEGATIVE
