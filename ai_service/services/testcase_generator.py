import uuid
import re
from typing import Any

from models.schemas import ProblemSchema, TestCaseResponse, TestCaseSchema
from services.groq_json import request_json, request_text


DEFAULT_CHECKER_CODE = """# Python checker
def check(input_data, expected, actual):
    return expected.strip() == actual.strip()
"""

MAX_TESTCASE_INPUT_CHARS = 20000


def generate_testcases(
    problem: ProblemSchema, count: int, include_edge_cases: bool
) -> TestCaseResponse:
    requested_count = max(1, min(count, 50))

    try:
        testcases = _generate_json_testcases(
            problem, requested_count, include_edge_cases
        )
        if testcases:
            return TestCaseResponse(
                testcases=testcases,
                checker_code=DEFAULT_CHECKER_CODE,
            )
    except Exception:
        pass

    try:
        testcases = _generate_text_testcases(
            problem, requested_count, include_edge_cases
        )
        if testcases:
            return TestCaseResponse(
                testcases=testcases,
                checker_code=DEFAULT_CHECKER_CODE,
            )
    except Exception:
        pass

    return TestCaseResponse(
        testcases=_sample_fallback_testcases(problem, requested_count),
        checker_code=DEFAULT_CHECKER_CODE,
    )


def _generate_json_testcases(
    problem: ProblemSchema, count: int, include_edge_cases: bool
) -> list[TestCaseSchema]:
    edge_case_text = " including edge cases" if include_edge_cases else ""
    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

Generate {count} test cases{edge_case_text}.
Generate valid stdin inputs only. Do not compute expected outputs.
Keep every generated input validation-friendly for a local reference solution:
- prefer small and medium sizes over maximum constraints
- when variables like n, m, q, t exist, keep them at most 200 unless the sample already exceeds that
- keep each full stdin under 5000 characters when possible
- include official sample inputs first when they are present and count allows
- still cover edge patterns such as minimum size, boundaries, repeated values, overlapping updates, and mixed query order

Return ONLY valid JSON with this structure:
{{
  "testcases": [
    {{
      "input_lines": ["..."],
      "description": "...",
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
    problem: ProblemSchema, count: int, include_edge_cases: bool
) -> list[TestCaseSchema]:
    edge_case_text = " including edge cases" if include_edge_cases else ""
    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

Generate {count} test cases{edge_case_text}.
Generate valid stdin inputs only. Do not compute expected outputs.
Keep every generated input validation-friendly for a local reference solution:
- prefer small and medium sizes over maximum constraints
- when variables like n, m, q, t exist, keep them at most 200 unless the sample already exceeds that
- keep each full stdin under 5000 characters when possible
- include official sample inputs first when they are present and count allows
- still cover edge patterns such as minimum size, boundaries, repeated values, overlapping updates, and mixed query order

Return only this plain text format, repeated once per test case:
###CASE
EDGE: true
DESC: short description
INPUT:
full stdin here
###END

Do not use JSON. Do not use markdown fences.
"""
    content = request_text([{"role": "user", "content": prompt}])
    return _parse_text_cases(content, count)


def _raw_testcases(data: dict[str, Any]) -> list[Any]:
    raw_cases = data.get("testcases") or data.get("cases") or data.get("tests") or []
    if isinstance(raw_cases, dict):
        raw_cases = list(raw_cases.values())
    return raw_cases if isinstance(raw_cases, list) else []


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
    problem: ProblemSchema, count: int
) -> list[TestCaseSchema]:
    testcases: list[TestCaseSchema] = []
    for index, sample_input in enumerate(problem.sample_inputs or []):
        if not _is_usable_input(sample_input):
            continue
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
