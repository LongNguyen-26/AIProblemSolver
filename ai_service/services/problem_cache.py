import hashlib
import json
from pathlib import Path
from typing import Any


CACHE_DIR = Path("cache/problems")
CACHE_DIR.mkdir(parents=True, exist_ok=True)


def problem_hash(problem_text: str) -> str:
    return hashlib.sha256((problem_text or "").encode("utf-8")).hexdigest()[:16]


def load_cache(problem_text: str) -> dict[str, Any] | None:
    path = _cache_path(problem_text)
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def save_cache(problem_text: str, data: dict[str, Any]) -> None:
    path = _cache_path(problem_text)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def _cache_path(problem_text: str) -> Path:
    return CACHE_DIR / f"{problem_hash(problem_text)}.json"
