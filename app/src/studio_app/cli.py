"""`uv run studio` — 화면 서버를 켜고 브라우저를 연다(이미 켜져 있으면 브라우저만)."""

from __future__ import annotations

import argparse
import logging
import threading
import urllib.request
import webbrowser

from studio_core.config import load_settings


def _running(url: str) -> bool:
    try:
        with urllib.request.urlopen(url + "api/status", timeout=1.5) as r:  # noqa: S310 (로컬 주소)
            return r.status == 200
    except OSError:
        return False


def main(argv: list[str] | None = None) -> None:
    """진입점."""
    parser = argparse.ArgumentParser(prog="studio", description="studio 화면(브라우저)")
    parser.add_argument("--port", type=int, default=8760)
    parser.add_argument("--no-browser", action="store_true", help="브라우저 자동으로 열지 않기")
    args = parser.parse_args(argv)
    url = f"http://127.0.0.1:{args.port}/"
    if _running(url):
        print(f"이미 켜져 있습니다 → {url}")
        if not args.no_browser:
            webbrowser.open(url)
        return

    import uvicorn

    from studio_app.server import create_app

    logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(name)s: %(message)s")
    settings = load_settings()
    app = create_app(settings)
    print(f"studio 화면: {url}")
    print("이 창을 닫거나 Ctrl+C를 누르면 꺼집니다.")
    if not args.no_browser:
        threading.Timer(1.2, webbrowser.open, [url]).start()
    uvicorn.run(app, host="127.0.0.1", port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
