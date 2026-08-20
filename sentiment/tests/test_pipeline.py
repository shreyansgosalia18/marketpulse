from unittest.mock import MagicMock, patch

from kafka.errors import KafkaError

from marketpulse_sentiment.pipeline import SentimentPipeline

VALID_NEWS_PAYLOAD = {"ticker": "AAPL", "uuid": "abc-123", "title": "Company beats estimates, shares soar"}


def _make_message(value):
    message = MagicMock()
    message.value = value
    return message


def _pipeline_with_mocks(consumer_messages, send_side_effect=None):
    with (
        patch("marketpulse_sentiment.pipeline.KafkaConsumer") as mock_consumer_cls,
        patch("marketpulse_sentiment.pipeline.KafkaProducer") as mock_producer_cls,
    ):
        mock_consumer = MagicMock()
        mock_consumer.__iter__.return_value = iter(consumer_messages)
        mock_consumer_cls.return_value = mock_consumer

        mock_producer = MagicMock()
        if send_side_effect is not None:
            mock_producer.send.side_effect = send_side_effect
        else:
            mock_producer.send.return_value.get.return_value = None
        mock_producer_cls.return_value = mock_producer

        pipeline = SentimentPipeline()
        return pipeline, mock_consumer, mock_producer


def test_scores_and_publishes_valid_news_event():
    pipeline, _, mock_producer = _pipeline_with_mocks([_make_message(VALID_NEWS_PAYLOAD)])

    result = pipeline.run_once()

    assert result.consumed == 1
    assert result.scored == 1
    assert result.published == 1
    assert result.ok
    mock_producer.send.assert_called_once()
    _, kwargs = mock_producer.send.call_args
    assert kwargs["key"] == "AAPL"
    assert kwargs["value"]["sentiment"] in ("positive", "negative", "neutral")


def test_skips_message_with_missing_required_fields():
    malformed = {"ticker": "AAPL"}  # missing uuid, title
    pipeline, _, mock_producer = _pipeline_with_mocks([_make_message(malformed)])

    result = pipeline.run_once()

    assert result.consumed == 1
    assert result.scored == 0
    assert result.published == 0
    assert len(result.errors) == 1
    mock_producer.send.assert_not_called()


def test_one_failure_does_not_stop_the_batch():
    def flaky_send(topic, key=None, value=None):
        future = MagicMock()
        if key == "BADTICKER":
            future.get.side_effect = KafkaError("boom")
        else:
            future.get.return_value = None
        return future

    messages = [
        _make_message({"ticker": "AAPL", "uuid": "1", "title": "Good news for Apple"}),
        _make_message({"ticker": "BADTICKER", "uuid": "2", "title": "Some headline"}),
        _make_message({"ticker": "MSFT", "uuid": "3", "title": "Good news for Microsoft"}),
    ]
    pipeline, _, mock_producer = _pipeline_with_mocks(messages, send_side_effect=flaky_send)

    result = pipeline.run_once()

    assert result.consumed == 3
    assert result.scored == 3
    assert result.published == 2
    assert len(result.errors) == 1
