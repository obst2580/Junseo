"""작은 HTML 트리와 선택자(표준 라이브러리 html.parser만 사용).

커뮤니티 글 상세 페이지에서 본문·댓글·공감 수를 뽑는 데 쓴다. 선택자는 꼭 필요한 만큼만:
`div.post_article`, `#bo_v_con`, `.comment_row .comment_content`(띄어 쓰면 안쪽), `a, b`(쉼표는 여러 후보).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from html.parser import HTMLParser

VOID = {
    "br",
    "img",
    "hr",
    "input",
    "meta",
    "link",
    "area",
    "base",
    "col",
    "embed",
    "param",
    "source",
    "track",
    "wbr",
}
SKIP = {"script", "style", "noscript", "template", "svg", "iframe"}
BLOCK = {
    "p", "div", "br", "li", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "article", "section",
    "blockquote", "dd", "dt", "table", "ul", "ol", "header", "footer", "main", "pre",
}  # fmt: skip
_AUTO_CLOSE = {"p", "li", "option", "dt", "dd"}
_CELL = {"td", "th"}


@dataclass(eq=False)
class Node:
    """요소 하나."""

    tag: str
    attrs: dict[str, str] = field(default_factory=dict)
    children: list[Node | str] = field(default_factory=list)
    parent: Node | None = None

    @property
    def id(self) -> str:
        """id 속성."""
        return self.attrs.get("id", "")

    @property
    def classes(self) -> list[str]:
        """class 목록."""
        return self.attrs.get("class", "").split()

    def nodes(self) -> list[Node]:
        """자기 자신을 뺀 모든 하위 요소(문서 순서)."""
        out: list[Node] = []
        stack = [c for c in reversed(self.children) if isinstance(c, Node)]
        while stack:
            n = stack.pop()
            out.append(n)
            stack.extend(c for c in reversed(n.children) if isinstance(c, Node))
        return out

    def ancestors(self) -> list[Node]:
        """부모부터 위로."""
        out, p = [], self.parent
        while p is not None:
            out.append(p)
            p = p.parent
        return out

    def depth(self) -> int:
        """깊이(루트 0)."""
        return len(self.ancestors())

    def text(self, skip: set[int] | None = None) -> str:
        """보이는 글(블록 요소마다 줄바꿈, 줄 안 공백 정리). skip: 빼고 읽을 요소 id(id(node)) 집합."""
        parts: list[str] = []

        def walk(n: Node) -> None:
            if skip and id(n) in skip:
                return
            if n.tag in BLOCK:
                parts.append("\n")
            for c in n.children:
                if isinstance(c, str):
                    parts.append(c)
                else:
                    walk(c)
            if n.tag in BLOCK:
                parts.append("\n")

        walk(self)
        lines = [re.sub(r"[ \t\r\f\v ]+", " ", ln).strip() for ln in "".join(parts).split("\n")]
        return "\n".join(ln for ln in lines if ln)

    def select(self, selector: str) -> list[Node]:
        """선택자에 맞는 하위 요소(문서 순서, 중복 없음)."""
        chains = [_parse_chain(part) for part in selector.split(",") if part.strip()]
        out: list[Node] = []
        for n in self.nodes():
            if any(_match_chain(n, ch) for ch in chains):
                out.append(n)
        return out

    def select_one(self, selector: str) -> Node | None:
        """첫 번째로 맞는 요소."""
        found = self.select(selector)
        return found[0] if found else None


_SIMPLE_RE = re.compile(r"^(?P<tag>[a-zA-Z][\w-]*|\*)?(?P<rest>(?:[#.][\w-]+)*)$")


@dataclass
class _Simple:
    tag: str | None
    id: str | None
    classes: list[str]


def _parse_simple(text: str) -> _Simple:
    m = _SIMPLE_RE.match(text)
    if not m:
        raise ValueError(f"선택자를 읽지 못했습니다: {text!r} (태그·#id·.class만 지원)")
    tag = m.group("tag")
    parts = re.findall(r"([#.])([\w-]+)", m.group("rest"))
    return _Simple(
        tag=None if tag in (None, "*") else tag.lower(),
        id=next((v for k, v in parts if k == "#"), None),
        classes=[v for k, v in parts if k == "."],
    )


def _parse_chain(text: str) -> list[_Simple]:
    return [_parse_simple(p) for p in text.split()]


def _match_simple(n: Node, s: _Simple) -> bool:
    if s.tag and n.tag != s.tag:
        return False
    if s.id and n.id != s.id:
        return False
    cls = n.classes
    return all(c in cls for c in s.classes)


def _match_chain(n: Node, chain: list[_Simple]) -> bool:
    if not chain or not _match_simple(n, chain[-1]):
        return False
    rest = chain[:-1]
    p = n.parent
    while rest and p is not None:
        if _match_simple(p, rest[-1]):
            rest = rest[:-1]
        p = p.parent
    return not rest


class _Builder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = Node("#root")
        self.stack: list[Node] = [self.root]
        self.skipping = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        if self.skipping:
            if tag in SKIP:
                self.skipping += 1
            return
        if tag in SKIP:
            self.skipping = 1
            return
        top = self.stack[-1]
        if tag in _AUTO_CLOSE and top.tag == tag:
            self.stack.pop()
        elif tag in _CELL and top.tag in _CELL:
            self.stack.pop()
        elif tag == "tr":
            while self.stack[-1].tag in (*_CELL, "tr") and len(self.stack) > 1:
                self.stack.pop()
        parent = self.stack[-1]
        node = Node(tag, {k.lower(): (v or "") for k, v in attrs}, parent=parent)
        parent.children.append(node)
        if tag not in VOID:
            self.stack.append(node)

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if self.skipping:
            return
        parent = self.stack[-1]
        parent.children.append(Node(tag.lower(), {k.lower(): (v or "") for k, v in attrs}, parent=parent))

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if self.skipping:
            if tag in SKIP:
                self.skipping -= 1
            return
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                return

    def handle_data(self, data: str) -> None:
        if not self.skipping and data:
            self.stack[-1].children.append(data)


def parse_html(text: str) -> Node:
    """HTML → 트리(깨진 HTML도 최대한 읽는다)."""
    b = _Builder()
    b.feed(text)
    b.close()
    return b.root
