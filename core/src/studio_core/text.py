"""한국어 텍스트 유틸: HTML 제거, 토큰화, 문장 분할, 존댓말 변환, 자막 줄 분할."""

from __future__ import annotations

import html
import math
import re
from collections import Counter
from collections.abc import Iterable

_TAG_RE = re.compile(r"<[^>]+>")
_WS_RE = re.compile(r"\s+")
_TOKEN_RE = re.compile(r"[가-힣A-Za-z0-9·]{2,}")

# 조사·어미 꼬리(긴 것부터 검사).
_SUFFIXES = sorted(
    [
        "에서는",
        "으로는",
        "에게서",
        "께서는",
        "이라는",
        "이라고",
        "라는",
        "라고",
        "에서",
        "에게",
        "께서",
        "으로",
        "부터",
        "까지",
        "처럼",
        "보다",
        "마저",
        "조차",
        "이나",
        "이며",
        "하고",
        "와의",
        "과의",
        "에는",
        "에도",
        "은",
        "는",
        "이",
        "가",
        "을",
        "를",
        "의",
        "에",
        "로",
        "와",
        "과",
        "도",
        "만",
        "했다",
        "한다",
        "된다",
        "됐다",
        "하는",
        "하며",
        "해야",
        "했고",
        "이다",
        "였다",
        "했던",
    ],
    key=len,
    reverse=True,
)

STOPWORDS: set[str] = {
    "오늘",
    "어제",
    "내일",
    "이날",
    "지난",
    "이번",
    "올해",
    "관련",
    "대해",
    "대한",
    "위해",
    "통해",
    "따르면",
    "밝혔다",
    "말했다",
    "전했다",
    "있다",
    "없다",
    "했다",
    "한다",
    "것으로",
    "것이",
    "가운데",
    "이후",
    "이전",
    "기자",
    "뉴스",
    "속보",
    "종합",
    "단독",
    "사진",
    "영상",
    "오전",
    "오후",
    "그러나",
    "하지만",
    "또한",
    "한편",
    "the",
    "and",
    "for",
    "with",
    "that",
    "this",
    "from",
    "are",
    "was",
    "has",
    "have",
    "will",
    "its",
    "his",
    "her",
    "after",
    "over",
    "into",
    "says",
    "said",
    "new",
    "news",
}


# 시간·과정 표현(군집 근거로 쓰면 무관한 기사를 묶는다)
STOPWORDS |= {
    "현지시간",
    "이날",
    "예정",
    "전망",
    "계획",
    "가운",
    "입장",
    "결과",
    "방안",
    "논의",
    "관측",
    "밝혀",
    "첫날",
    "이틀째",
    "사흘째",
    "시작",
    "지난해",
    "직전",
    "당일",
    "이어졌",
    "나왔",
    "나온",
    "열린",
    "열린다",
}


def strip_html(text: str | None) -> str:
    """HTML 태그·엔티티를 제거하고 공백을 정리한다."""
    if not text:
        return ""
    no_tags = _TAG_RE.sub(" ", text)
    return _WS_RE.sub(" ", html.unescape(no_tags)).strip()


def normalize_title(title: str) -> str:
    """[속보] 같은 머리표와 언론사 꼬리표를 정리한다."""
    t = strip_html(title)
    t = re.sub(r"^\s*[\[\(【<]\s*[^\]\)】>]{1,8}\s*[\]\)】>]\s*", "", t)
    t = re.sub(r"\s*[-|]\s*[^-|]{1,12}$", "", t) if " - " in t or " | " in t else t
    return t.strip()


_KEEP_UI = ("회의", "합의", "논의", "결의", "협의", "동의", "주의", "정의", "민의", "의의", "강의", "질의")


def _strip_suffix(tok: str) -> str:
    for suf in _SUFFIXES:
        if len(tok) > len(suf) + 1 and tok.endswith(suf):
            if suf == "의" and tok.endswith(_KEEP_UI):  # 본회의·합의 등은 '의'가 조사가 아니다
                continue
            return tok[: -len(suf)]
    return tok


_NUMERIC_TOKEN = re.compile(r"^\d+(일|월|년|시|분|명|건|개|차|주|%|원|억|조|만)?$")


def tokenize(text: str) -> list[str]:
    """2자 이상 토큰을 뽑고 조사를 떼어낸다(불용어·날짜/수량 토큰 제외)."""
    out: list[str] = []
    for raw in _TOKEN_RE.findall(strip_html(text)):
        tok = _strip_suffix(raw)
        tok = tok.lower() if tok.isascii() else tok
        if len(tok) >= 2 and tok not in STOPWORDS and not _NUMERIC_TOKEN.match(tok):
            out.append(tok)
    return out


def extract_keywords(text: str, k: int = 8, extra_stop: Iterable[str] = ()) -> list[str]:
    """빈도 기반 키워드 상위 k개."""
    stop = set(extra_stop)
    counts = Counter(t for t in tokenize(text) if t not in stop)
    return [w for w, _ in counts.most_common(k)]


def jaccard(a: Iterable[str], b: Iterable[str]) -> float:
    """두 집합의 자카드 유사도."""
    sa, sb = set(a), set(b)
    if not sa or not sb:
        return 0.0
    return len(sa & sb) / len(sa | sb)


def idf_table(docs: list[set[str]]) -> dict[str, float]:
    """문서 집합의 IDF."""
    n = max(len(docs), 1)
    df: Counter[str] = Counter()
    for d in docs:
        df.update(d)
    return {t: math.log((n + 1) / (c + 0.5)) for t, c in df.items()}


_SENT_SPLIT_RE = re.compile(r"(?<=[.!?…])\s+|(?<=다\.)|(?<=요\.)|\n+")


def split_sentences(text: str) -> list[str]:
    """문장 단위로 나눈다."""
    parts = [p.strip() for p in _SENT_SPLIT_RE.split(strip_html(text)) if p and p.strip()]
    return [p for p in parts if len(p) >= 4]


# ---- 존댓말 변환 -------------------------------------------------------------

_HANGUL_BASE = 0xAC00


def _decompose(ch: str) -> tuple[int, int, int] | None:
    code = ord(ch) - _HANGUL_BASE
    if not 0 <= code < 11172:
        return None
    return code // 588, (code % 588) // 28, code % 28


def _compose(cho: int, jung: int, jong: int) -> str:
    return chr(_HANGUL_BASE + cho * 588 + jung * 28 + jong)


_JONG_N, _JONG_B, _JONG_SS = 4, 17, 20
_VOWEL_ADJ_STEMS = {"니", "크", "쁘", "프", "르", "치", "끄", "뜨", "트", "하"}


def _is_polite(s: str) -> bool:
    m = re.search(r"(.)니다[.!?]?[\"'”’]?$", s)
    if not m:
        return False
    parts = _decompose(m.group(1))
    return m.group(1) == "습" or (parts is not None and parts[2] == _JONG_B)


def to_polite(sentence: str) -> str:
    """기사체 '~다.' 종결을 '~습니다/~ㅂ니다.'로 바꾼다. 해당 없으면 그대로."""
    s = sentence.rstrip()
    m = re.search(r"(.)다([.!?]?)([\"'”’]?)$", s)
    if not m:
        return s
    prev, punct, quote = m.group(1), m.group(2) or ".", m.group(3)
    head = s[: m.start()]
    if _is_polite(s):
        return s
    if prev == "는" and head and _decompose(head[-1]) and _decompose(head[-1])[2] != 0:
        return f"{head}습니다{punct}{quote}"  # 앉는다 → 앉습니다
    if prev == "는" and head:
        return f"{head[:-1]}{_with_jong(head[-1], _JONG_B)}니다{punct}{quote}"  # 가는다(드묾)
    parts = _decompose(prev)
    if parts is None:
        return s
    cho, jung, jong = parts
    if jong == _JONG_N:  # 한다 → 합니다, 된다 → 됩니다
        return f"{head}{_compose(cho, jung, _JONG_B)}니다{punct}{quote}"
    if jong == 0:
        if prev == "이" or prev in _VOWEL_ADJ_STEMS:  # 이다 → 입니다, 크다 → 큽니다
            return f"{head}{_compose(cho, jung, _JONG_B)}니다{punct}{quote}"
        return f"{head}{prev}입니다{punct}{quote}"  # 고비다 → 고비입니다(체언 + 서술격)
    return f"{head}{prev}습니다{punct}{quote}"  # 했다 → 했습니다, 있다 → 있습니다


def _with_jong(ch: str, jong: int) -> str:
    parts = _decompose(ch)
    if parts is None:
        return ch
    return _compose(parts[0], parts[1], jong)


# ---- 자막 줄 분할 -------------------------------------------------------------

_BREAK_AFTER_RE = re.compile(r"(,|·|고|며|지만|는데|면서|해서|하자|으나|려고|도록) ")


def wrap_subtitle(sentence: str, limit: int = 25) -> list[str]:
    """한 문장을 자막 단위(limit자 이하) 줄로 나눈다.

    절 경계(쉼표, '~고', '~며', '~지만' 등)를 우선으로 끊고, 그래도 길면
    어절 경계에서 끊는다.
    """
    s = sentence.strip()
    if len(s) <= limit:
        return [s] if s else []
    # 1) 절 경계 후보 위치
    cuts = [m.end() for m in _BREAK_AFTER_RE.finditer(s)]
    lines: list[str] = []
    start = 0
    while len(s) - start > limit:
        window_end = start + limit
        candidates = [c for c in cuts if start + limit // 2 <= c <= window_end + 1]
        if candidates:
            cut = candidates[-1]
        else:
            space = s.rfind(" ", start, window_end + 1)
            cut = space + 1 if space > start else window_end
        lines.append(s[start:cut].strip())
        start = cut
    rest = s[start:].strip()
    if rest:
        lines.append(rest)
    return [ln for ln in lines if ln]


def count_chars(lines: Iterable[str]) -> int:
    """대본 글자 수(공백 포함, 주석·헤더 제외)."""
    total = 0
    for ln in lines:
        t = ln.strip()
        if not t or t.startswith("#"):
            continue
        total += len(t)
    return total


_NUM_RE = re.compile(r"\d[\d,.]*")


def numbers_in(text: str) -> list[str]:
    """문장 속 숫자 토큰(쉼표 제거)."""
    return [n.replace(",", "").rstrip(".") for n in _NUM_RE.findall(text)]


_QUOTE_RE = re.compile(r"[\"“]([^\"”]{2,})[\"”]")


def quotes_in(text: str) -> list[str]:
    """큰따옴표 인용구."""
    return [q.strip() for q in _QUOTE_RE.findall(text)]


def has_batchim(word: str) -> bool:
    """마지막 글자 받침 여부(한글이 아니면 False)."""
    for ch in reversed(word.strip()):
        parts = _decompose(ch)
        if parts is not None:
            return parts[2] != 0
        if ch.isalnum():
            return False
    return False


def josa(word: str, pair: str) -> str:
    """'을/를', '이/가', '은/는', '과/와', '으로/로' 중 알맞은 조사를 붙인다."""
    a, b = pair.split("/")
    if pair == "으로/로":
        last = _decompose(word.strip()[-1]) if word.strip() else None
        use_a = has_batchim(word) and not (last is not None and last[2] == 8)  # ㄹ 받침은 '로'
        return word + (a if use_a else b)
    return word + (a if has_batchim(word) else b)


def short_name(name: str, limit: int = 14) -> str:
    """주제명을 제목용으로 줄인다(따옴표 제거, 쉼표·말줄임 앞까지, 어절 경계)."""
    s = re.sub(r"[\"'“”‘’「」『』\[\]]", "", name).strip()
    if " — " in s:  # 일정명 '기관 — 안건'은 안건 쪽이 주제다
        head, _, tail = s.partition(" — ")
        s = tail if len(tail) >= 4 else head
    s = re.split(r"…|\.\.\.", s)[0].replace(",", "").strip()
    if len(s) <= limit:
        return s
    cut = s.rfind(" ", 0, limit + 1)
    return s[: cut if cut > 4 else limit].strip()
