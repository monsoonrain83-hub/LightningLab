#!/usr/bin/env python3
"""Static safety and JavaScript syntax check for the offline expert annotator.

This is NOT a substitute for testing video playback in a real browser.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
from html.parser import HTMLParser
from pathlib import Path


class Scripts(HTMLParser):
    def __init__(self):
        super().__init__()
        self.inside = False
        self.blocks = []
        self.external = False

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag in ("iframe", "object", "embed"):
            self.external = True
        if tag == "script":
            if attrs.get("src"):
                self.external = True
            self.inside = True
            self.blocks.append("")

    def handle_endtag(self, tag):
        if tag == "script":
            self.inside = False

    def handle_data(self, data):
        if self.inside:
            self.blocks[-1] += data


def validate(path: Path) -> None:
    html = path.read_text(encoding="utf-8")
    parsed = Scripts()
    parsed.feed(html)
    if parsed.external or len(parsed.blocks) != 1:
        raise ValueError("Offline annotator must contain exactly one local inline script")
    js = parsed.blocks[0]
    if any(token in js for token in ("fetch(", "XMLHttpRequest", "WebSocket(", "sendBeacon(", "eval(")):
        raise ValueError("Annotation tool may not transmit video data or evaluate injected JS")
    if not all(key in html for key in ("expert_intervals.csv", "start_ms", "end_ms", "video_id")):
        raise ValueError("Missing expert temporal annotation contract fields")
    with tempfile.TemporaryDirectory() as temporary:
        source = Path(temporary) / "annotation.js"
        source.write_text(js, encoding="utf-8")
        result = subprocess.run(["node", "--check", str(source)],
                                capture_output=True, text=True, timeout=15)
        if result.returncode != 0:
            raise ValueError("Expert annotation JavaScript syntax invalid: " + result.stderr)


if __name__ == "__main__":
    target = Path(__file__).with_name("lightning_annotator.html")
    try:
        validate(target)
        print("Offline annotation JavaScript syntax and static privacy checks passed")
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("Annotator validation failed:", error, file=sys.stderr)
        raise SystemExit(1)
