import uuid
import re
from typing import Any, Iterable

from models.schemas import ProblemSchema, TestCaseResponse, TestCaseSchema
from services.groq_json import request_json, request_text
from services.problem_classifier import classify_problem_fast
from services.problem_taxonomy import type_strategy


DEFAULT_CHECKER_CODE = """# Python checker
def check(input_data, expected, actual):
    return expected.strip() == actual.strip()
"""

MAX_TESTCASE_INPUT_CHARS = 200000
MAX_GENERATION_ATTEMPTS = 3
MAX_EXISTING_INPUTS_IN_PROMPT = 20
MAX_EXISTING_INPUT_CHARS_IN_PROMPT = 1000


def generate_testcases(
    problem: ProblemSchema,
    count: int,
    include_edge_cases: bool,
    profile: str = "SMALL",
    existing_inputs: list[str] | None = None,
) -> TestCaseResponse:
    requested_count = max(1, min(count, 50))
    normalized_profile = _normalize_profile(profile)
    collected: list[TestCaseSchema] = []
    seen_inputs = _input_key_set(existing_inputs or [])

    for _ in range(MAX_GENERATION_ATTEMPTS):
        still_needed = requested_count - len(collected)
        if still_needed <= 0:
            break

        avoid_inputs = list(seen_inputs)
        generated: list[TestCaseSchema] = []
        try:
            generated = _generate_json_testcases(
                problem,
                still_needed,
                include_edge_cases,
                normalized_profile,
                avoid_inputs,
            )
        except Exception:
            pass

        _append_unique_testcases(collected, generated, seen_inputs)
        still_needed = requested_count - len(collected)
        if still_needed <= 0:
            break

        try:
            generated = _generate_text_testcases(
                problem,
                still_needed,
                include_edge_cases,
                normalized_profile,
                list(seen_inputs),
            )
            _append_unique_testcases(collected, generated, seen_inputs)
        except Exception:
            pass

    if len(collected) < requested_count:
        fallback_cases = _sample_fallback_testcases(
            problem,
            requested_count - len(collected),
            seen_inputs,
        )
        _append_unique_testcases(collected, fallback_cases, seen_inputs)

    return TestCaseResponse(
        testcases=collected[:requested_count],
        checker_code=DEFAULT_CHECKER_CODE,
    )


def _generate_json_testcases(
    problem: ProblemSchema,
    count: int,
    include_edge_cases: bool,
    profile: str,
    existing_inputs: list[str] | None = None,
) -> list[TestCaseSchema]:
    edge_case_text = " including edge cases" if include_edge_cases else ""
    profile_guidance = _profile_guidance(profile)
    type_context = _problem_type_context(problem, profile)
    existing_inputs_guidance = _existing_inputs_guidance(existing_inputs or [], profile)
    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

CRITICAL: You MUST return EXACTLY {count} test cases in the JSON array.
If you cannot think of {count} distinct cases, generate valid variations with
different inputs. Never return fewer than {count} items.

Generate exactly {count} unique test cases{edge_case_text}.
Generate valid stdin inputs only. Do not compute expected outputs.
Each test case must be a complete standalone stdin for one program run. If the
format has a leading t/T number of test cases, make that number consistent with
the scenarios included in that one stdin.
{profile_guidance}
{type_context}
{existing_inputs_guidance}
If official samples are included, they count toward {count}; fill all remaining
slots with newly generated, distinct inputs.

Return ONLY valid JSON with this structure:
{{
  "testcases": [
    {{
      "input_lines": ["..."],
      "description": "... name the profile and the type-specific edge/killer pattern targeted ...",
      "is_edge_case": false
    }}
  ]
}}
Do not include checker code.
Do not include expected outputs.
Do not put newline characters inside JSON string values. Use input_lines arrays instead.
"""

    data = request_json([{"role": "user", "content": prompt}])
    return _normalize_testcases(_raw_testcases(data), count)


def _generate_text_testcases(
    problem: ProblemSchema,
    count: int,
    include_edge_cases: bool,
    profile: str,
    existing_inputs: list[str] | None = None,
) -> list[TestCaseSchema]:
    edge_case_text = " including edge cases" if include_edge_cases else ""
    profile_guidance = _profile_guidance(profile)
    type_context = _problem_type_context(problem, profile)
    existing_inputs_guidance = _existing_inputs_guidance(existing_inputs or [], profile)
    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

CRITICAL: You MUST return EXACTLY {count} test cases.
If you cannot think of {count} distinct cases, generate valid variations with
different inputs. Never return fewer than {count} items.

Generate exactly {count} unique test cases{edge_case_text}.
Generate valid stdin inputs only. Do not compute expected outputs.
Each test case must be a complete standalone stdin for one program run. If the
format has a leading t/T number of test cases, make that number consistent with
the scenarios included in that one stdin.
{profile_guidance}
{type_context}
{existing_inputs_guidance}
If official samples are included, they count toward {count}; fill all remaining
slots with newly generated, distinct inputs.

Return only this plain text format, repeated once per test case:
###CASE
EDGE: true
DESC: short description
INPUT:
full stdin here
###END

Do not use JSON. Do not use markdown fences.
The DESC line must name the profile and the type-specific edge/killer pattern targeted.
"""
    content = request_text([{"role": "user", "content": prompt}])
    return _parse_text_cases(content, count)


def _raw_testcases(data: dict[str, Any]) -> list[Any]:
    raw_cases = data.get("testcases") or data.get("cases") or data.get("tests") or []
    if isinstance(raw_cases, dict):
        raw_cases = list(raw_cases.values())
    return raw_cases if isinstance(raw_cases, list) else []


def _append_unique_testcases(
    collected: list[TestCaseSchema],
    candidates: list[TestCaseSchema],
    seen_inputs: set[str],
) -> None:
    for candidate in candidates or []:
        key = _input_key(candidate.input)
        if not key or key in seen_inputs:
            continue
        collected.append(candidate)
        seen_inputs.add(key)


def _input_key_set(values: Iterable[str]) -> set[str]:
    return {key for key in (_input_key(value) for value in values or []) if key}


def _input_key(value: str) -> str:
    if not value:
        return ""
    normalized = value.replace("\r\n", "\n").replace("\r", "\n")
    normalized = "\n".join(line.rstrip() for line in normalized.split("\n"))
    return normalized.strip()


def _existing_inputs_guidance(existing_inputs: list[str], profile: str) -> str:
    unique_inputs = list(dict.fromkeys(_input_key(value) for value in existing_inputs))
    unique_inputs = [value for value in unique_inputs if value]
    if not unique_inputs:
        return ""

    shown = unique_inputs[:MAX_EXISTING_INPUTS_IN_PROMPT]
    lines = [
        "Already generated inputs (DO NOT repeat these):",
    ]
    for index, input_text in enumerate(shown, start=1):
        clipped = input_text
        if len(clipped) > MAX_EXISTING_INPUT_CHARS_IN_PROMPT:
            clipped = clipped[:MAX_EXISTING_INPUT_CHARS_IN_PROMPT] + "\n..."
        lines.append(f"{index}. {clipped}")

    remaining = len(unique_inputs) - len(shown)
    if remaining > 0:
        lines.append(f"... and {remaining} more existing inputs.")
    lines.append(f"Generate NEW and DISTINCT inputs for profile {profile}.")
    return "\n".join(lines)


def _problem_type_context(problem: ProblemSchema, profile: str) -> str:
    classification = classify_problem_fast(problem)
    strategy = type_strategy(classification.primary_type)
    edge_cases = "\n".join(f"- {value}" for value in strategy["edge_cases"])
    profile_value = (profile or "SMALL").upper()
    if profile_value == "KILLER":
        priority = (
            "For KILLER tests, prioritize the killer strategy and mention it in each description."
        )
    elif profile_value == "MEDIUM":
        priority = (
            "For MEDIUM tests, mix normal cases with the type-specific edge cases below."
        )
    else:
        priority = (
            "For SMALL tests, include several of the type-specific edge cases below and "
            "mention the chosen edge pattern in each description."
        )

    return f"""
Problem type classification:
- Primary type: {classification.primary_type}
- Secondary type: {classification.secondary_type or "none"}
- Confidence: {classification.confidence:.2f}
- Input generator approach: {strategy["input_generator_hint"]}

Type-specific edge cases:
{edge_cases}

Killer test strategy:
- {strategy["killer_strategy"]}

{priority}
"""


def _normalize_testcases(raw_cases: list[Any], count: int) -> list[TestCaseSchema]:
    testcases: list[TestCaseSchema] = []
    for raw in raw_cases:
        if not isinstance(raw, dict):
            continue

        input_text = _multiline_value(raw, "input", "input_lines", "stdin")
        if not _is_usable_input(input_text):
            continue

        testcases.append(
            TestCaseSchema(
                id=_new_id(),
                input=input_text.strip(),
                expected_output="",
                description=_string_value(raw.get("description") or raw.get("desc")),
                is_edge_case=_bool_value(
                    raw.get("is_edge_case", raw.get("edgeCase", raw.get("edge")))
                ),
            )
        )
        if len(testcases) >= count:
            break
    return testcases


def _multiline_value(raw: dict[str, Any], *keys: str) -> str:
    for key in keys:
        if key in raw:
            value = raw.get(key)
            if isinstance(value, list):
                return "\n".join(_string_value(item) for item in value)
            return _string_value(value)
    return ""


def _parse_text_cases(content: str, count: int) -> list[TestCaseSchema]:
    testcases: list[TestCaseSchema] = []
    blocks = re.findall(r"###CASE\s*(.*?)###END", content, flags=re.DOTALL | re.IGNORECASE)
    for block in blocks:
        input_match = re.search(
            r"INPUT:\s*\n(.*)",
            block,
            flags=re.DOTALL | re.IGNORECASE,
        )
        if not input_match:
            continue

        desc_match = re.search(r"^DESC:\s*(.*)$", block, flags=re.MULTILINE | re.IGNORECASE)
        edge_match = re.search(r"^EDGE:\s*(.*)$", block, flags=re.MULTILINE | re.IGNORECASE)
        input_text = _remove_optional_output_section(input_match.group(1)).strip()
        if not _is_usable_input(input_text):
            continue

        testcases.append(
            TestCaseSchema(
                id=_new_id(),
                input=input_text,
                expected_output="",
                description=desc_match.group(1).strip() if desc_match else "",
                is_edge_case=_bool_value(edge_match.group(1) if edge_match else False),
            )
        )
        if len(testcases) >= count:
            break
    return testcases


def _sample_fallback_testcases(
    problem: ProblemSchema, count: int, seen_inputs: set[str] | None = None
) -> list[TestCaseSchema]:
    testcases: list[TestCaseSchema] = []
    seen = set(seen_inputs or set())
    for index, sample_input in enumerate(problem.sample_inputs or []):
        if not _is_usable_input(sample_input):
            continue
        key = _input_key(sample_input)
        if not key or key in seen:
            continue
        seen.add(key)
        testcases.append(
            TestCaseSchema(
                id=_new_id(),
                input=sample_input.strip(),
                expected_output="",
                description=f"Sample test case {index + 1}",
                is_edge_case=False,
            )
        )
        if len(testcases) >= count:
            return testcases

    if testcases:
        return testcases

    return [
        TestCaseSchema(
            id=_new_id(),
            input="",
            expected_output="",
            description="No generated test cases were available; add this case manually.",
            is_edge_case=True,
        )
    ]


def _remove_optional_output_section(value: str) -> str:
    return re.split(r"\nOUTPUT:\s*\n", value, maxsplit=1, flags=re.IGNORECASE)[0]


def _normalize_profile(profile: str) -> str:
    value = (profile or "SMALL").strip().upper()
    if value in {"MEDIUM", "STRONG", "LARGE", "KILLER"}:
        return "KILLER" if value in {"STRONG", "LARGE", "KILLER"} else "MEDIUM"
    return "SMALL"


def _profile_guidance(profile: str) -> str:
    if profile == "KILLER":
        return """
This is the LARGE/KILLER performance phase. Generate adversarial inputs intended to expose TLE:
- push the main size variables close to the statement limits when possible
- if the real limit is enormous and raw stdin would be too large, use the largest input that fits under 200000 characters
- prefer dense workloads: many queries, long ranges, repeated updates, all-equal data,
  alternating data, nested ranges, sorted/reversed data, and worst-case-looking patterns
- avoid tiny or sample-like inputs
- do not include official sample inputs unless count is very small
"""

    if profile == "MEDIUM":
        return """
This is the MEDIUM phase. Generate normal and edge-case inputs for logic coverage:
- use sizes clearly bigger than samples but still easy for a trusted optimized oracle to run
- when variables like n, m, q, t exist, prefer values from 50 to 500 when the format allows it
- keep each full stdin under 50000 characters
- include boundary cases, overlapping ranges, repeated values, mixed query orders,
  alternating structures, sorted/reversed data, and multiple independent scenarios
- avoid maximum-only stress; save that for the KILLER phase
"""

    return """
This is the SAMPLE/SMALL stress-validation phase. Keep every input suitable for brute force:
- prefer tiny and small sizes over maximum constraints
- when variables like n, m, q, t exist, keep them at most 60 unless the sample already exceeds that
- keep each full stdin under 5000 characters
- include official sample inputs first when they are present and count allows
- still cover edge patterns such as minimum size, boundaries, repeated values,
  overlapping updates, alternating values, and mixed query order
"""


def _is_usable_input(value: str) -> bool:
    return bool(value and value.strip() and len(value) <= MAX_TESTCASE_INPUT_CHARS)


def _string_value(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def _bool_value(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"true", "yes", "1", "edge"}
    return bool(value)


def _new_id() -> str:
    return f"tc_{str(uuid.uuid4())[:8]}"
