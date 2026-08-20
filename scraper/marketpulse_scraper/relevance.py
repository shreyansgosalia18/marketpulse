import re

from .models import NewsArticle

_COMPANY_SUFFIX_RE = re.compile(
    r"[,.]?\s*\b(inc|corp|corporation|co|company|ltd|plc|group|holdings|holding)\b\.?\s*$",
    re.IGNORECASE,
)


def core_company_name(company_name: str) -> str:
    """Strip a trailing corporate suffix (Inc., Corp., Ltd., ...) for looser matching."""
    stripped = _COMPANY_SUFFIX_RE.sub("", company_name).strip()
    return stripped or company_name


def _mentions(title: str, needle: str) -> bool:
    return re.search(rf"\b{re.escape(needle)}\b", title, re.IGNORECASE) is not None


def filter_relevant(ticker: str, company_name: str | None, articles: list[NewsArticle]) -> list[NewsArticle]:
    """Keep only articles whose title mentions the ticker symbol or the company name.

    Yahoo's search endpoint doesn't guarantee returned articles are actually
    about the queried ticker — it falls back to generic/trending results for
    unrecognized queries. This filters to what can be verified from the
    title alone; it's a keyword match, not semantic relevance.
    """
    needles = {ticker}
    if company_name:
        needles.add(company_name)
        needles.add(core_company_name(company_name))

    return [article for article in articles if any(_mentions(article.title, needle) for needle in needles if needle)]
