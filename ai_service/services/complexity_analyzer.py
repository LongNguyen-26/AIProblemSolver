import re
from typing import Any

from models.schemas import ComplexityInfo, ProblemSchema
from services.groq_json import request_json


COMPLEXITY_PROMPT = """
Analyze this competitive programming problem and determine:
1. The expected optimal time complexity.
2. A slow but correct target complexity that should TLE on large inputs.
3. The main input size N inferred from constraints.
4. A concrete algorithmic TLE strategy whose work depends on input size.

Return ONLY valid JSON:
{
  "optimal_complexity": "O(n log n)",
  "tle_target_complexity": "O(n^2)",
  "max_n": 100000,
  "tle_strategy": "nested_loop_bruteforce",
  "tle_explanation": "why this approach is slow but still correct"
}

Rules:
- The TLE strategy must be a real slower algorithm, not sleep, dummy loops, or fixed delay.
- If the optimal solution is likely O(n log n), prefer O(n^2) or O(n*q).
- If the problem is combinatorial with small exact search possible, prefer backtracking
  or exhaustive enumeration for the slow correct version.
- If constraints are unclear, choose a conservative input-dependent brute force strategy.

Problem:
{problem_text}
"""


def analyze_complexity(problem: ProblemSchema) -> ComplexityInfo:
    prompt = COMPLEXITY_PROMPT.replace("{problem_text}", _format_problem(problem))
    try:
        data = request_json([{"role": "user", "content": prompt}])
        return _normalize_complexity_info(data, problem)
    except Exception:
        return _heuristic_complexity_info(problem)


def _normalize_complexity_info(data: dict[str, Any], problem: ProblemSchema) -> ComplexityInfo:
    fallback = _heuristic_complexity_info(problem)
    if not isinstance(data, dict):
        return fallback

    max_n = _int_value(data.get("max_n"), fallback.max_n)
    return ComplexityInfo(
        optimal_complexity=_string_value(
            data.get("optimal_complexity"),
            fallback.optimal_complexity,
        ),
        tle_target_complexity=_string_value(
            data.get("tle_target_complexity"),
            fallback.tle_target_complexity,
        ),
        max_n=max_n,
        tle_strategy=_string_value(data.get("tle_strategy"), fallback.tle_strategy),
        tle_explanation=_string_value(
            data.get("tle_explanation"),
            fallback.tle_explanation,
        ),
    )


def _heuristic_complexity_info(problem: ProblemSchema) -> ComplexityInfo:
    text = _format_problem(problem).lower()
    max_n = _extract_max_n(text)

    if any(keyword in text for keyword in ["permutation", "subset", "choose", "combination"]):
        return ComplexityInfo(
            optimal_complexity="problem-dependent",
            tle_target_complexity="O(2^n) or O(n!) on small inputs",
            max_n=max_n,
            tle_strategy="exhaustive_backtracking",
            tle_explanation=(
                "Enumerate candidate states or permutations exactly. This is correct for "
                "small inputs but grows exponentially with the input."
            ),
        )

    if any(keyword in text for keyword in ["graph", "tree", "edge", "path", "vertex"]):
        return ComplexityInfo(
            optimal_complexity="O((n + m) log n) or O(n + m)",
            tle_target_complexity="O(n * (n + m))",
            max_n=max_n,
            tle_strategy="repeated_graph_search",
            tle_explanation=(
                "Run a fresh BFS/DFS or relaxation from many start points instead of "
                "using a single optimized graph pass."
            ),
        )

    if any(keyword in text for keyword in ["query", "update", "range"]):
        return ComplexityInfo(
            optimal_complexity="O((n + q) log n)",
            tle_target_complexity="O(n * q)",
            max_n=max_n,
            tle_strategy="naive_per_query_scan",
            tle_explanation=(
                "Answer each query by scanning or recomputing over the affected range. "
                "The work is correct but scales with both input size and query count."
            ),
        )

    if any(keyword in text for keyword in ["sort", "sorted", "inversion", "array", "sequence"]):
        return ComplexityInfo(
            optimal_complexity="O(n log n)",
            tle_target_complexity="O(n^2)",
            max_n=max_n,
            tle_strategy="quadratic_nested_loop",
            tle_explanation=(
                "Use pairwise comparison or quadratic dynamic programming instead of "
                "the optimized sort/data-structure approach."
            ),
        )

    return ComplexityInfo(
        optimal_complexity="unknown",
        tle_target_complexity="O(n^2)",
        max_n=max_n,
        tle_strategy="input_dependent_bruteforce",
        tle_explanation=(
            "Use a straightforward complete algorithm with nested loops or repeated "
            "simulation so runtime grows with input size."
        ),
    )


def _extract_max_n(text: str) -> int:
    candidates: list[int] = []
    for match in re.finditer(r"(?:n|m|q|k)\s*(?:<=|≤|leq|up to|at most)\s*([0-9][0-9^]*)", text):
        candidates.append(_parse_bound(match.group(1)))
    for match in re.finditer(r"10\^([0-9]+)", text):
        candidates.append(10 ** int(match.group(1)))
    return max([value for value in candidates if value > 0], default=0)


def _parse_bound(value: str) -> int:
    text = value.strip()
    if "^" in text:
        base, exponent = text.split("^", 1)
        try:
            return int(base) ** int(exponent)
        except Exception:
            return 0
    try:
        return int(re.sub(r"[^0-9]", "", text))
    except Exception:
        return 0


def _format_problem(problem: ProblemSchema) -> str:
    return "\n".join(
        [
            "Title: " + (problem.title or ""),
            "Description: " + (problem.description or ""),
            "Input: " + (problem.input_format or ""),
            "Output: " + (problem.output_format or ""),
            "Constraints: " + "; ".join(problem.constraints or []),
        ]
    )


def _string_value(value: Any, fallback: str) -> str:
    if value is None:
        return fallback
    text = str(value).strip()
    return text or fallback


def _int_value(value: Any, fallback: int) -> int:
    try:
        return int(value)
    except Exception:
        return fallback
