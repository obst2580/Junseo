import io
import json

import httpx
from PIL import Image

from studio_core.fixtures import FixtureTransport


def _transport(tmp_path, routes: str) -> FixtureTransport:
    (tmp_path / "routes.yaml").write_text(routes, encoding="utf-8")
    return FixtureTransport(tmp_path)


async def test_routes_params_and_generators(tmp_path):
    (tmp_path / "a.json").write_text('{"q": "a"}', encoding="utf-8")
    (tmp_path / "stats.json").write_text(
        json.dumps(
            {
                "v1": {
                    "title": "t",
                    "description": "",
                    "channelId": "c",
                    "channelTitle": "ch",
                    "publishedAt": "2026-10-08T00:00:00Z",
                    "viewCount": 5,
                    "likeCount": 1,
                    "commentCount": 0,
                }
            }
        ),
        encoding="utf-8",
    )
    t = _transport(
        tmp_path,
        """routes:
  - {match: 'news\\.json', params: {query: a}, file: a.json}
  - {match: 'v3/videos', params: {part: 'snippet,statistics'}, generate: youtube_stats, data: stats.json}
  - {match: 'v3/videos', generate: youtube_videos}
  - {match: 'v3/search', generate: youtube_search}
  - {match: 'customsearch', generate: google_image}
  - {match: 'search/image', generate: naver_image}
  - {match: 'wikimedia', generate: wikimedia}
  - {match: 'img\\.example', generate: image}
""",
    )
    async with httpx.AsyncClient(transport=t) as c:
        assert (await c.get("https://x/news.json", params={"query": "a"})).json() == {"q": "a"}
        assert (await c.get("https://x/news.json", params={"query": "b"})).status_code == 404
        st = (await c.get("https://x/v3/videos", params={"part": "snippet,statistics", "id": "v1,v2"})).json()
        assert st["items"][0]["statistics"]["viewCount"] == "5" and len(st["items"]) == 1
        vids = (await c.get("https://x/v3/videos", params={"part": "contentDetails", "id": "abc"})).json()
        assert vids["items"][0]["contentDetails"]["duration"].startswith("PT")
        s = (await c.get("https://x/v3/search", params={"q": "국회", "maxResults": 3})).json()
        assert len(s["items"]) == 3
        g = (await c.get("https://customsearch/v1", params={"q": "국회"})).json()
        assert g["items"][0]["image"]["contextLink"]
        n = (await c.get("https://x/search/image", params={"query": "국회"})).json()
        assert n["items"][0]["sizewidth"]
        w = (await c.get("https://commons.wikimedia.org/w/api.php", params={"gsrsearch": "x"})).json()
        assert w["query"]["pages"]
        img = await c.get("https://img.example.com/a.jpg", params={"w": 320, "h": 200, "label": "국회"})
        assert Image.open(io.BytesIO(img.content)).size == (320, 200)
        post = await c.post("https://x/news.json", json={"query": "a"})
        assert post.json() == {"q": "a"}
