import hashlib
import re

from models.schemas import ProblemSchema
from services.groq_json import request_json
from services.problem_classifier import apply_classification, apply_keyword_classification


ANALYZE_SYSTEM_PROMPT = """Extract competitive-programming statement fields.
Return ONLY JSON with: title, description, input_format, output_format,
constraints, sample_inputs, sample_outputs. No markdown."""

MAX_ANALYZE_PROMPT_CHARS = 12000
_analysis_cache: dict[str, dict] = {}


def analyze_problem(text: str) -> ProblemSchema:
    cache_key = _analysis_cache_key(text)
    if cache_key in _analysis_cache:
        return ProblemSchema(**_analysis_cache[cache_key])

    heuristic = _parse_problem_heuristic(text)
    try:
        data = request_json(
            [
                {"role": "system", "content": ANALYZE_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": f"Analyze this problem:\n\n{_analysis_prompt_text(text)}",
                },
            ],
            max_tokens=1600,
        )
        problem = apply_classification(_merge_problem(ProblemSchema(**data), heuristic))
    except Exception:
        problem = apply_keyword_classification(heuristic)
    _analysis_cache[cache_key] = problem.model_dump()
    return problem


def fallback_analyze_problem(text: str, reason: str = "") -> ProblemSchema:
    problem = _parse_problem_heuristic(text)
    if not problem.description and reason:
        problem.description = reason
    return apply_keyword_classification(problem)


def _analysis_cache_key(text: str) -> str:
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()


def _analysis_prompt_text(text: str) -> str:
    value = text or ""
    if len(value) <= MAX_ANALYZE_PROMPT_CHARS:
        return value
    head = value[:10000].rstrip()
    tail = value[-1800:].lstrip()
    return head + "\n\n...[statement truncated; ending follows]...\n\n" + tail


def _parse_problem_heuristic(text: str) -> ProblemSchema:
    raw_lines = _non_empty_lines(text)
    footer_title = _footer_title(raw_lines)
    lines = [
        line
        for line in raw_lines
        if not _is_noise_line(line) and line != footer_title
    ]

    title = _infer_title(lines, footer_title)
    sections = {
        "description": [],
        "input_format": [],
        "output_format": [],
        "constraints": [],
    }
    sample_inputs: list[list[str]] = []
    sample_outputs: list[list[str]] = []
    combined_samples: list[list[str]] = []
    current = "description"

    for line in lines:
        kind = _heading_kind(line)
        if kind:
            if kind == "sample_input":
                sample_inputs.append([])
            elif kind == "sample_output":
                sample_outputs.append([])
            elif kind == "sample_combined":
                combined_samples.append([])
            current = kind
            continue

        if current == "sample_input":
            if not sample_inputs:
                sample_inputs.append([])
            sample_inputs[-1].append(line)
        elif current == "sample_output":
            if not sample_outputs:
                sample_outputs.append([])
            sample_outputs[-1].append(line)
        elif current == "sample_combined":
            if not combined_samples:
                combined_samples.append([])
            combined_samples[-1].append(line)
        elif current in sections:
            if line != title:
                sections[current].append(line)

    parsed_inputs = [_join(block) for block in sample_inputs if _join(block)]
    parsed_outputs = [_join(block) for block in sample_outputs if _join(block)]
    for block in combined_samples:
        sample_input, sample_output = _split_combined_sample(block)
        if sample_input:
            parsed_inputs.append(sample_input)
        if sample_output:
            parsed_outputs.append(sample_output)

    description = _join(sections["description"])
    if not description:
        description = _description_before_input(lines, title)

    input_format = _join(sections["input_format"])
    output_format = _join(sections["output_format"])
    constraints = _constraint_lines(sections)

    return ProblemSchema(
        title=title or "Problem (parsed fallback)",
        description=description,
        input_format=input_format,
        output_format=output_format,
        constraints=constraints,
        sample_inputs=parsed_inputs,
        sample_outputs=parsed_outputs,
    )


def _merge_problem(primary: ProblemSchema, fallback: ProblemSchema) -> ProblemSchema:
    primary.title = _prefer_text(primary.title, fallback.title)
    primary.description = _prefer_text(primary.description, fallback.description)
    primary.input_format = _prefer_text(primary.input_format, fallback.input_format)
    primary.output_format = _prefer_text(primary.output_format, fallback.output_format)
    if not primary.constraints:
        primary.constraints = fallback.constraints
    if not primary.sample_inputs:
        primary.sample_inputs = fallback.sample_inputs
    if not primary.sample_outputs:
        primary.sample_outputs = fallback.sample_outputs
    return primary


def _non_empty_lines(text: str) -> list[str]:
    return [
        line.strip()
        for line in (text or "").replace("\r\n", "\n").replace("\r", "\n").split("\n")
        if line.strip()
    ]


def _heading_kind(line: str) -> str:
    normalized = line.strip().lower().rstrip(":")
    compact = " ".join(normalized.split())
    if "sample input" in compact and "sample output" in compact:
        return "sample_combined"
    if _matches_heading(compact, "sample input"):
        return "sample_input"
    if _matches_heading(compact, "sample output"):
        return "sample_output"
    if compact in {"input", "input format"}:
        return "input_format"
    if compact in {"output", "output format"}:
        return "output_format"
    if compact in {"constraint", "constraints"}:
        return "constraints"
    return ""


def _matches_heading(value: str, heading: str) -> bool:
    return value == heading or value.startswith(heading + " ")


def _infer_title(lines: list[str], footer_title: str) -> str:
    if footer_title:
        return _clean_title(footer_title)
    for line in lines[:12]:
        if _heading_kind(line) or _is_noise_line(line):
            continue
        if 4 <= len(line) <= 90 and not line.endswith("."):
            return _clean_title(line)
    return ""


def _footer_title(lines: list[str]) -> str:
    for index, line in enumerate(lines):
        if _is_blank_page_line(line) and index > 0:
            for previous in reversed(lines[:index]):
                if previous and not _is_noise_line(previous):
                    return previous
    return ""


def _clean_title(value: str) -> str:
    title = (value or "").strip()
    title = re.sub(r"\s+\d+$", "", title).strip()
    title = re.sub(r"^(?:[A-Za-z]+\s+){1,4}20\d{2}\s+", "", title).strip()
    return title[:120].strip()


def _description_before_input(lines: list[str], title: str) -> str:
    result: list[str] = []
    for line in lines:
        if _heading_kind(line) == "input_format":
            break
        if line != title and not _heading_kind(line):
            result.append(line)
    return _join(result)


def _constraint_lines(sections: dict[str, list[str]]) -> list[str]:
    constraints: list[str] = []
    for line in sections.get("constraints", []):
        _append_unique(constraints, line)
    for group in ("input_format", "description"):
        for line in sections.get(group, []):
            if _looks_like_constraint(line):
                _append_unique(constraints, line)
    return constraints


def _looks_like_constraint(line: str) -> bool:
    lowered = (line or "").lower()
    return (
        "<=" in line
        or ">=" in line
        or "\u2264" in line
        or "\u2265" in line
        or "constraint" in lowered
        or "at most" in lowered
        or "up to" in lowered
    )


def _split_combined_sample(block: list[str]) -> tuple[str, str]:
    values = [line for line in block if line and not _is_noise_line(line)]
    if not values:
        return "", ""
    if len(values) == 1:
        parts = values[0].split(maxsplit=1)
        if len(parts) == 2 and _looks_like_sample_input(parts[0]):
            return parts[0], parts[1]
        return values[0], ""
    return values[0], _join(values[1:])


def _looks_like_sample_input(value: str) -> bool:
    return bool(value and re.fullmatch(r"[-+]?\d+(?:\s+[-+]?\d+)*", value.strip()))


def _prefer_text(value: str, fallback: str) -> str:
    text = (value or "").strip()
    if text and text.lower() != "problem (unparsed)":
        return text
    return fallback or text


def _append_unique(values: list[str], value: str) -> None:
    text = (value or "").strip()
    if text and text not in values:
        values.append(text)


def _join(lines: list[str]) -> str:
    return "\n".join(line for line in lines if line).strip()


def _is_noise_line(line: str) -> bool:
    return _is_blank_page_line(line) or line.strip().isdigit()


def _is_blank_page_line(line: str) -> bool:
    return "intentionally left blank" in (line or "").lower()
