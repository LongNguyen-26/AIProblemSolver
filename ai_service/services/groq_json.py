import json
import logging
import threading
import time
from typing import Any, Dict, List

from openai import OpenAI as GroqClient

from config import GROQ_BASE_URL, GROQ_MODEL, MAX_TOKENS, require_groq_api_key

logger = logging.getLogger(__name__)

MAX_RETRIES = 4
BASE_DELAY_SECONDS = 2.0
MAX_CONCURRENT_GROQ_CALLS = 2
_groq_semaphore = threading.BoundedSemaphore(MAX_CONCURRENT_GROQ_CALLS)


JSON_RETRY_MESSAGE = """
Return one compact, syntactically valid JSON object only.
Do not use markdown fences. Do not add comments.
Escape all newline characters inside JSON string values as \\n.
"""


def request_json(
    messages: List[Dict[str, str]],
    max_tokens: int | None = None,
) -> Dict[str, Any]:
    client = GroqClient(api_key=require_groq_api_key(), base_url=GROQ_BASE_URL)
    last_error: Exception | None = None

    for force_json_mode in (True, False):
        try:
            content = _completion(
                client,
                messages,
                force_json_mode=force_json_mode,
                append_json_retry=not force_json_mode,
                max_tokens=max_tokens,
            )
            return _loads_json_object(content)
        except Exception as exc:
            last_error = exc
            if not force_json_mode:
                break
            if not _should_retry_without_json_mode(exc):
                raise

    raise RuntimeError(f"AI did not return valid JSON: {last_error}") from last_error


def request_text(
    messages: List[Dict[str, str]],
    max_tokens: int | None = None,
) -> str:
    client = GroqClient(api_key=require_groq_api_key(), base_url=GROQ_BASE_URL)
    return _completion(
        client,
        messages,
        force_json_mode=False,
        max_tokens=max_tokens,
    )


def _completion(
    client: GroqClient,
    messages: List[Dict[str, str]],
    force_json_mode: bool,
    append_json_retry: bool = False,
    max_tokens: int | None = None,
) -> str:
    request_messages = list(messages)
    if append_json_retry:
        request_messages = [
            *request_messages,
            {"role": "user", "content": JSON_RETRY_MESSAGE},
        ]

    kwargs: Dict[str, Any] = {
        "model": GROQ_MODEL,
        "max_tokens": _bounded_max_tokens(max_tokens),
        "temperature": 0,
        "messages": request_messages,
    }
    if "gpt-oss" in GROQ_MODEL:
        kwargs["reasoning_effort"] = "low"
    if force_json_mode:
        kwargs["response_format"] = {"type": "json_object"}

    response = _create_completion_with_retry(client, kwargs)
    content = response.choices[0].message.content or ""
    if not content.strip():
        raise ValueError("AI returned empty content")
    return content


def _create_completion_with_retry(client: GroqClient, kwargs: Dict[str, Any]):
    delay = BASE_DELAY_SECONDS
    with _groq_semaphore:
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                return client.chat.completions.create(**kwargs)
            except Exception as exc:
                if not _is_rate_limit_error(exc) or attempt >= MAX_RETRIES:
                    raise
                logger.warning(
                    "Groq rate limit (attempt %s/%s), retrying in %.0fs.",
                    attempt,
                    MAX_RETRIES,
                    delay,
                )
                time.sleep(delay)
                delay *= 2


def _bounded_max_tokens(value: int | None) -> int:
    if value is None:
        return MAX_TOKENS
    return max(1, min(int(value), MAX_TOKENS))


def _is_rate_limit_error(exc: Exception) -> bool:
    text = str(exc).lower()
    return "429" in text or "rate limit" in text or "too many" in text


def _loads_json_object(content: str) -> Dict[str, Any]:
    try:
        data = json.loads(_strip_code_fence(content))
    except json.JSONDecodeError:
        data = json.loads(_extract_first_json_object(content))
    if not isinstance(data, dict):
        raise ValueError("AI response JSON root must be an object")
    return data


def _strip_code_fence(content: str) -> str:
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if len(lines) >= 3 and lines[-1].strip() == "```":
            return "\n".join(lines[1:-1]).strip()
    return text


def _extract_first_json_object(content: str) -> str:
    text = _strip_code_fence(content)
    start = text.find("{")
    if start < 0:
        raise ValueError("No JSON object found in AI response")

    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == "\"":
                in_string = False
            continue

        if char == "\"":
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start : index + 1]

    raise ValueError("Unclosed JSON object in AI response")


def _should_retry_without_json_mode(exc: Exception) -> bool:
    text = str(exc)
    return (
        isinstance(exc, (json.JSONDecodeError, ValueError))
        or "json_validate_failed" in text
        or "failed_generation" in text
        or "Failed to validate JSON" in text
    )
