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
MAX_KILLER_STRATEGY_BUFFER = 2

KILLER_STRATEGIES = {
    "ARRAY_SEQUENCE": [
        "All elements equal to MAX_VALUE with n=MAX_N",
        "Strictly increasing sequence with n=MAX_N",
        "Strictly decreasing sequence with n=MAX_N",
        "Alternating maximum/minimum values with n=MAX_N",
        "Single large positive value followed by zeros",
    ],
    "GRAPH_UNDIRECTED": [
        "Long path graph (bamboo) with n=MAX_N",
        "Star graph with one center and n-1 leaves",
        "Dense graph near MAX_M without duplicate edges",
        "Two dense components connected by a single bridge",
        "Cycle plus many chord edges",
    ],
    "GRAPH_DIRECTED": [
        "Long directed chain with n=MAX_N",
        "DAG with many forward edges in topological order",
        "Dense strongly connected components",
        "One source connected to many sinks with back edges",
    ],
    "TREE": [
        "Bamboo tree (single path) with n=MAX_N",
        "Star tree with n=MAX_N",
        "Balanced binary tree near MAX_N",
        "Caterpillar tree with a long spine and many leaves",
    ],
    "DP_1D": [
        "All states reachable with n=MAX_N",
        "All item values identical to force tie-heavy transitions",
        "Capacity or target at maximum with small repeated weights",
        "Impossible boundary state near maximum constraints",
    ],
    "DP_2D": [
        "Square grid near maximum area",
        "Single row with maximum length",
        "Single column with maximum length",
        "Checkerboard obstacle/value pattern",
        "Uniform grid values that maximize transitions",
    ],
    "DP_BITMASK": [
        "n at maximum bitmask limit with all costs equal",
        "Dense compatibility matrix with many valid subsets",
        "Near-complete assignment choices to maximize transitions",
    ],
    "MATH_NUMBER_THEORY": [
        "Largest prime-like value near MAX_VALUE",
        "Large composite with many small prime factors",
        "Values near maximum that risk overflow",
        "Many queries all near maximum values",
    ],
    "MATH_COMBINATORICS": [
        "n=MAX_N and k=n/2",
        "n=MAX_N with k=0 and k=n boundary mix",
        "Many large test scenarios requiring modular arithmetic",
    ],
    "STRING": [
        "Maximum length all-same character string",
        "Maximum length highly periodic string",
        "Alternating characters at maximum length",
        "Pattern almost matches text at every position",
    ],
    "GEOMETRY": [
        "Maximum points all collinear",
        "Maximum points with many duplicates if allowed",
        "Coordinates at positive and negative extremes",
        "All points on convex hull",
    ],
    "GREEDY": [
        "Maximum intervals all sharing the same endpoint",
        "Tie-heavy tasks with identical profit/deadline",
        "All intervals overlapping",
        "Sorted input that breaks wrong tie-breaking",
    ],
    "BINARY_SEARCH": [
        "Answer exactly at lower boundary",
        "Answer exactly at upper boundary",
        "All values equal with n=MAX_N",
        "Feasibility changes only at the final position",
    ],
    "DATA_STRUCTURE": [
        "n=q=MAX with alternating update/query operations",
        "All queries on full range",
        "All updates on the same index",
        "Nested ranges with many repeated values",
    ],
    "CONSTRUCTIVE": [
        "Maximum n with tight constraints",
        "Minimal impossible boundary case",
        "Maximum impossible-looking case",
        "Tie-heavy parameters with many valid constructions",
    ],
    "GAME_THEORY": [
        "Maximum n symmetric position",
        "Large losing position near boundary",
        "Large winning position requiring non-greedy move",
        "Many piles/states with repeated values",
    ],
}


def generate_testcases(
    problem: ProblemSchema,
    count: int,
    include_edge_cases: bool,
    profile: str = "SMALL",
    existing_inputs: list[str] | None = None,
) -> TestCaseResponse:
    requested_count = max(1, min(count, 50))
    normalized_profile = _normalize_profile(profile)

    if normalized_profile == "KILLER":
        killer_cases = generate_killer_cases(problem, requested_count, existing_inputs or [])
        return TestCaseResponse(
            testcases=killer_cases[:requested_count],
            checker_code=DEFAULT_CHECKER_CODE,
        )

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


def generate_killer_cases(
    problem: ProblemSchema,
    count: int,
    existing_inputs: list[str] | None = None,
) -> list[TestCaseSchema]:
    requested_count = max(1, min(count, 50))
    collected: list[TestCaseSchema] = []
    seen_inputs = _input_key_set(existing_inputs or [])
    strategies = _killer_strategies_for(problem)

    for strategy in strategies[: requested_count * MAX_KILLER_STRATEGY_BUFFER]:
        case = generate_single_killer_case(problem, strategy, list(seen_inputs))
        _append_unique_testcases(collected, [case] if case else [], seen_inputs)
        if len(collected) >= requested_count:
            break

    attempts = 0
    while len(collected) < requested_count and attempts < MAX_GENERATION_ATTEMPTS:
        generated = _generate_random_killer_cases(
            problem,
            requested_count - len(collected),
            list(seen_inputs),
        )
        _append_unique_testcases(collected, generated, seen_inputs)
        attempts += 1

    if len(collected) < requested_count:
        fallback_cases = _sample_fallback_testcases(
            problem,
            requested_count - len(collected),
            seen_inputs,
        )
        _append_unique_testcases(collected, fallback_cases, seen_inputs)

    return collected[:requested_count]


def generate_single_killer_case(
    problem: ProblemSchema,
    strategy: str,
    existing_inputs: list[str] | None = None,
) -> TestCaseSchema | None:
    existing_keys = _input_key_set(existing_inputs or [])
    classification = classify_problem_fast(problem)
    max_n = _extract_max_n(problem.constraints or [])
    prompt = f"""
You are generating a KILLER test case for a competitive programming problem.

Problem:
{problem.model_dump_json(indent=2)}

Problem type: {classification.primary_type}
Constraints:
{_constraint_text(problem)}
Strategy: {strategy}

YOUR TASK: Generate EXACTLY ONE complete stdin input that:
1. Uses maximum possible values when the input format allows it (target n={max_n or "MAX_N from constraints"})
2. Follows this exact strategy: {strategy}
3. Is syntactically valid per the input format
4. Would cause O(n^2), O(n*m), recursion-depth, or otherwise weak algorithms to fail
5. Stays under {MAX_TESTCASE_INPUT_CHARS} characters of raw stdin

CRITICAL OUTPUT FORMAT:
- Output ONLY the raw test input text
- NO explanation, NO labels, NO expected output, NO markdown
- Start directly with the first line of input

Input format:
{problem.input_format or "Infer from the statement JSON above."}

{_existing_inputs_guidance(list(existing_keys), "KILLER")}

Generate the killer test case now:
"""
    try:
        content = request_text([{"role": "user", "content": prompt}])
    except Exception:
        return None

    input_text = _clean_raw_input_response(content)
    if not _is_usable_input(input_text):
        return None
    if _input_key(input_text) in existing_keys:
        return None

    return TestCaseSchema(
        id=_new_id(),
        input=input_text.strip(),
        expected_output="",
        description=f"[KILLER] {strategy}",
        is_edge_case=True,
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


def _generate_random_killer_cases(
    problem: ProblemSchema,
    count: int,
    existing_inputs: list[str],
) -> list[TestCaseSchema]:
    strategies = _killer_strategies_for(problem)
    hint = "; ".join(strategies[:3])
    try:
        cases = _generate_text_testcases(
            problem,
            count,
            True,
            "KILLER",
            existing_inputs,
        )
    except Exception:
        cases = []

    for case in cases:
        if not case.description:
            case.description = f"[KILLER] Random maximum-size adversarial case: {hint}"
        elif "[KILLER]" not in case.description.upper():
            case.description = "[KILLER] " + case.description
        case.is_edge_case = True
    return cases


def _killer_strategies_for(problem: ProblemSchema) -> list[str]:
    classification = classify_problem_fast(problem)
    problem_type = classification.primary_type or problem.problem_type or "ARRAY_SEQUENCE"
    strategies = list(KILLER_STRATEGIES.get(problem_type, KILLER_STRATEGIES["ARRAY_SEQUENCE"]))
    taxonomy_strategy = type_strategy(problem_type).get("killer_strategy", "")
    if taxonomy_strategy:
        strategies.insert(0, taxonomy_strategy)
    return list(dict.fromkeys(strategy for strategy in strategies if strategy))


def _constraint_text(problem: ProblemSchema) -> str:
    constraints = problem.constraints or []
    if constraints:
        return "\n".join(f"- {value}" for value in constraints)
    return "- No explicit constraints parsed; infer realistic maximums from the statement."


def _extract_max_n(constraints: list[str]) -> int:
    best = 0
    for constraint in constraints or []:
        text = str(constraint).replace(",", "")
        value_pattern = r"((?:\d+\s*(?:\*|x)\s*)?10\s*\^\s*\d+|\d+(?:e\d+)?)"
        patterns = [
            rf"\bn\s*(?:<=|<)\s*{value_pattern}",
            rf"\bN\s*(?:<=|<)\s*{value_pattern}",
            rf"1\s*<=\s*n\s*<=\s*{value_pattern}",
            rf"1\s*<=\s*N\s*<=\s*{value_pattern}",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                best = max(best, _parse_bound_value(match.group(1)))
    return best


def _parse_bound_value(value: str) -> int:
    text = (value or "").replace(" ", "").replace(",", "").lower()
    text = text.replace("x", "*")
    try:
        if "e" in text:
            return int(float(text))
        power_match = re.fullmatch(r"(?:(\d+)\*)?10\^(\d+)", text)
        if power_match:
            factor = int(power_match.group(1) or "1")
            return factor * (10 ** int(power_match.group(2)))
        return int(text)
    except Exception:
        return 0


def _clean_raw_input_response(content: str) -> str:
    text = _strip_code_fence(content or "")
    text = _remove_optional_output_section(text)
    text = re.sub(r"^\s*(input|stdin)\s*:\s*", "", text, flags=re.IGNORECASE)
    return text.strip()


def _strip_code_fence(content: str) -> str:
    text = (content or "").strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if len(lines) >= 3 and lines[-1].strip() == "```":
            return "\n".join(lines[1:-1]).strip()
        return text.strip("`").strip()
    return text


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
