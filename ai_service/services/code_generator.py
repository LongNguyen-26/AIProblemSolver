from typing import Any

from models.schemas import CodeGenResponse, ComplexityInfo, ProblemSchema, TestCaseSchema
from services.groq_json import request_json, request_text


TYPE_INSTRUCTIONS = {
    "AC": (
        "Write a CORRECT, optimal solution. It must pass all test cases. "
        "Do not use heuristics, probabilistic assumptions, or arguments like "
        "'rare in practice'. Handle adversarial inputs within the stated constraints. "
        "For constructive or multiple-solution problems, print a deterministic "
        "canonical valid solution and print an impossibility marker such as "
        "NO SOLUTION only after the algorithm has proven impossibility."
    ),
    "ORACLE": (
        "Write a complete, correct reference solution used only to compute expected "
        "outputs for validation-sized generated tests. Prioritize exact problem "
        "semantics, simplicity, and debuggability over asymptotic optimality. If the "
        "official constraints are huge but the input is small, use a straightforward "
        "simulation or brute-force approach when that is less error-prone. For "
        "constructive problems, exhaustively search a valid output on small inputs "
        "when feasible; do not print NO SOLUTION unless the search proves it."
    ),
    "BRUTE": (
        "Write a complete brute-force reference solution for small validation inputs. "
        "Prioritize obvious correctness over performance. Directly simulate the "
        "statement whenever possible, even if the complexity would be too slow for "
        "official maximum constraints. For constructive or multiple-solution "
        "problems, enumerate possible outputs for small inputs and print a valid "
        "solution if one exists; print NO SOLUTION only after exhaustive proof."
    ),
    "BRUTE_ALT": (
        "Write a second independent brute-force reference solution for small "
        "validation inputs. Use a different implementation style from the obvious "
        "primary brute force when possible. Prioritize exact statement semantics over "
        "speed. For constructive or multiple-solution problems, independently "
        "search for a valid output on small inputs instead of guessing impossibility."
    ),
    "ORACLE_ALT": (
        "Write a complete, correct reference solution used only to cross-check another "
        "oracle on validation-sized generated tests. Use an independent implementation "
        "style or algorithmic approach from the obvious primary solution. Prioritize "
        "exact problem semantics and clarity over speed. For constructive problems, "
        "produce a deterministic valid output and avoid unsupported NO SOLUTION."
    ),
    "OPTIMIZED_ALT": (
        "Write a complete optimized solution intended for larger generated tests. Use "
        "an independent implementation style from a standard solution where possible. "
        "Do not use heuristics or assumptions; it must follow the exact problem "
        "semantics. For constructive or multiple-solution problems, print a "
        "deterministic canonical valid solution."
    ),
    "WA": (
        "Write a solution with exactly one subtle bug that gives WRONG ANSWER "
        "on specific cases while still compiling and following the correct "
        "overall algorithm structure."
    ),
    "TLE": (
        "Write a solution that is logically CORRECT and should pass sample, small, "
        "and medium tests, but is intentionally too slow for large/killer tests due "
        "to poor asymptotic complexity (for example O(n^2) or O(n*q) where an "
        "optimized solution is expected). Do not use infinite loops, sleeps, random "
        "behavior, deliberate wrong answers, or compile/runtime errors. If the "
        "program finishes on a test case, its output must be accepted; a completed "
        "WRONG ANSWER is not a valid TLE solution. For constructive or "
        "multiple-solution problems, use a complete slow search or the same "
        "canonical construction style as a correct solution, not an unsupported "
        "one-variable heuristic."
    ),
    "GENERATOR": (
        "Write a Python random input generator for this competitive programming "
        "problem. The generator reads an optional integer seed from stdin and "
        "prints exactly one complete valid stdin instance for the target problem. "
        "Keep generated values small enough for brute-force reference solutions "
        "but diverse enough to expose edge cases. Print only the generated input."
    ),
}


WA_SYSTEM_PROMPT = """You are an expert competitive programmer tasked with writing
INTENTIONALLY WRONG solutions. Your bugs must be subtle, not obvious, but must
cause wrong output on specific inputs. The solution must compile and run."""


WA_USER_PROMPT = """Problem:
{problem_description}

Input format: {input_format}
Output format: {output_format}
Constraints:
{constraints}

Sample or validation test cases (your code MUST fail with wrong output on at
least one of these if cases are provided):
{sample_cases_formatted}

TASK: Write a {language} solution that:
1. Compiles and runs without errors
2. Uses the CORRECT algorithm structure, not random output or brute guessing
3. Contains EXACTLY ONE subtle bug from this list, choosing the most natural fit:
   - Off-by-one: wrong loop bound (i < n instead of i <= n, or vice versa)
   - Wrong initialization: variable init to 0 instead of -1e18, or INT_MAX instead of 0
   - Missing edge case: does not handle n=1, all-equal array, negative values, or empty input
   - Wrong formula: small arithmetic error, such as n*(n-1)/2 instead of n*(n+1)/2
   - Wrong greedy choice: correct greedy structure but picks min instead of max, or vice versa
   - Wrong DP transition: dp[i] uses wrong previous state, such as dp[i-2] instead of dp[i-1]
4. Matches the local runner conventions: Java must use public class Main, C++ must
   define int main(), and Python must read from standard input

REQUIRED: Add these three comment lines at the very top of your code using the
target language line comment prefix ({comment_prefix}):
{comment_prefix} BUG_TYPE: [name of bug category chosen above]
{comment_prefix} BUG_DESC: [one sentence: what is wrong and why it fails]
{comment_prefix} FAILS_ON: [describe the shape of input that exposes this bug]

Output ONLY the source code. No markdown fences and no explanation outside the
required comments.{retry_note}"""


def generate_code(
    problem: ProblemSchema,
    code_type: str,
    language: str,
    validation_cases: list[TestCaseSchema] | None = None,
    complexity_info: dict[str, Any] | ComplexityInfo | None = None,
    error_log: str = "",
) -> CodeGenResponse:
    code_type = (code_type or "AC").upper()
    if code_type == "WA":
        code = _generate_wa_code(problem, language, validation_cases or [], error_log)
        if not code.strip():
            raise RuntimeError("AI response did not include generated WA code")
        return CodeGenResponse(
            code=code,
            language=language,
            type=code_type,
            explanation=_wa_explanation(code),
        )

    instruction = _instruction_for_code_type(code_type, complexity_info)
    validation_text = _validation_cases_prompt(validation_cases or [], code_type)
    error_feedback_text = _error_feedback_prompt(error_log)
    prompt = f"""
{instruction}

Problem:
{problem.model_dump_json(indent=2)}

Language: {language}

{validation_text}

{error_feedback_text}

Before returning, verify mentally that the source follows the input format exactly,
prints only the required output, and matches all sample inputs/outputs embedded in
the problem statement. Return a full compilable program, not pseudocode.

Return ONLY valid JSON:
{{
  "code": "... full source code ...",
  "explanation": "... brief explanation of the approach and why it is {code_type} ..."
}}
Escape all line breaks inside the code string as \\n.
"""

    data = request_json([{"role": "user", "content": prompt}])
    code = _find_text_value(data, "code", "source_code", "solution")
    explanation = _find_text_value(data, "explanation", "notes")
    if not code.strip():
        fallback_instruction = "\n\n".join(
            text for text in [instruction, validation_text, error_feedback_text] if text.strip()
        )
        code = _generate_code_text(problem, code_type, language, fallback_instruction)
        explanation = explanation or "Generated as source code after the JSON response omitted code."
    if not code.strip():
        raise RuntimeError("AI response did not include generated code")

    return CodeGenResponse(
        code=code,
        language=language,
        type=code_type,
        explanation=explanation,
    )


def _generate_wa_code(
    problem: ProblemSchema,
    language: str,
    validation_cases: list[TestCaseSchema],
    error_log: str = "",
) -> str:
    prompt = _build_wa_prompt(problem, language, validation_cases, error_log)
    content = request_text(
        [
            {"role": "system", "content": WA_SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ]
    )
    return _strip_code_fence(content)


def _build_wa_prompt(
    problem: ProblemSchema,
    language: str,
    sample_cases: list[TestCaseSchema],
    error_log: str = "",
) -> str:
    comment_prefix = _line_comment_prefix(language)
    retry_note = (
        "\n\nPREVIOUS ATTEMPT FEEDBACK:\n"
        + error_log.strip()
        + "\nChoose a bug that specifically fails one of the passed cases above."
        if error_log and error_log.strip()
        else ""
    )
    return WA_USER_PROMPT.format(
        problem_description=value_or_na(problem.description),
        input_format=value_or_na(problem.input_format),
        output_format=value_or_na(problem.output_format),
        constraints="\n".join(problem.constraints or []) or "N/A",
        sample_cases_formatted=_format_wa_sample_cases(sample_cases),
        language=language,
        comment_prefix=comment_prefix,
        retry_note=retry_note,
    )


def _format_wa_sample_cases(sample_cases: list[TestCaseSchema]) -> str:
    examples: list[str] = []
    for index, case in enumerate((sample_cases or [])[:3], start=1):
        input_text = _case_text(case, "input").strip()
        expected = _case_text(case, "expected_output").strip()
        if not input_text or not expected:
            continue
        examples.append(
            f"Case #{index}\n"
            f"Input:\n{_truncate_prompt_text(input_text, 2500)}\n"
            f"Expected output:\n{_truncate_prompt_text(expected, 1000)}"
        )
    if not examples:
        return (
            "No validation cases were provided. Choose a natural edge shape from "
            "the constraints and make the single bug fail there."
        )
    return "\n\n".join(examples)


def _line_comment_prefix(language: str) -> str:
    normalized = (language or "").lower().strip()
    if normalized in {"python", "py"}:
        return "#"
    return "//"


def _case_text(case: TestCaseSchema | dict[str, Any], field: str) -> str:
    if isinstance(case, dict):
        value = case.get(field, "")
    else:
        value = getattr(case, field, "")
    return "" if value is None else str(value)


def _truncate_prompt_text(value: str, limit: int) -> str:
    text = value.strip()
    if len(text) <= limit:
        return text
    return text[:limit].rstrip() + "\n...[truncated]"


def _wa_explanation(code: str) -> str:
    metadata = []
    for line in value_or_na(code).splitlines()[:8]:
        stripped = line.strip()
        if "BUG_TYPE:" in stripped or "BUG_DESC:" in stripped or "FAILS_ON:" in stripped:
            metadata.append(stripped)
    if metadata:
        return "Intentional WA candidate. " + " ".join(metadata)
    return "Intentional WA candidate with a subtle single bug."


def value_or_na(value: str) -> str:
    return value if value and str(value).strip() else "N/A"


def _generate_code_text(
    problem: ProblemSchema, code_type: str, language: str, instruction: str
) -> str:
    prompt = f"""
{instruction}

Problem:
{problem.model_dump_json(indent=2)}

Language: {language}

Return ONLY the complete source code. Do not use markdown fences.
"""
    content = request_text([{"role": "user", "content": prompt}])
    return _strip_code_fence(content)


def _instruction_for_code_type(
    code_type: str,
    complexity_info: dict[str, Any] | ComplexityInfo | None,
) -> str:
    if code_type != "TLE":
        return TYPE_INSTRUCTIONS.get(code_type, TYPE_INSTRUCTIONS["AC"])

    info = _normalize_complexity_info(complexity_info)
    return f"""
Generate a solution for this problem that is:
1. CORRECT: it produces the right answer for every valid input.
2. SLOW: it uses {info.tle_strategy} with complexity {info.tle_target_complexity}.
3. Input-dependent: the slowness must come from the algorithm and scale with the
   real input size, not from artificial work.

Complexity target:
- Expected optimal complexity: {info.optimal_complexity}
- Slow TLE target complexity: {info.tle_target_complexity}
- Approximate max N: {info.max_n}
- Strategy: {info.tle_strategy}
- Why this is slow but correct: {info.tle_explanation}

Forbidden:
- sleep(), usleep(), Thread.sleep(), delay, timers, or wall-clock checks
- infinite loops
- fixed dummy loops that do not depend on parsed input
- random or nondeterministic behavior
- deliberate wrong answers, compile errors, or runtime errors

If the program finishes on a validation case, its output must match the expected
output exactly. For large inputs, it should become slow because the selected
algorithm has poor asymptotic complexity.
""".strip()


def _normalize_complexity_info(
    complexity_info: dict[str, Any] | ComplexityInfo | None,
) -> ComplexityInfo:
    if isinstance(complexity_info, ComplexityInfo):
        return complexity_info
    if isinstance(complexity_info, dict) and complexity_info:
        try:
            return ComplexityInfo(**complexity_info)
        except Exception:
            pass
    return ComplexityInfo()


def _error_feedback_prompt(error_log: str) -> str:
    if not error_log or not error_log.strip():
        return ""
    return (
        "Previous attempt failed local validation. Fix these exact issues in the "
        "next solution:\n" + error_log.strip()
    )


def fix_code(
    problem: ProblemSchema,
    broken_code: str,
    error_feedback: str,
    code_type: str,
    language: str = "python",
) -> str:
    instruction = TYPE_INSTRUCTIONS.get(code_type, TYPE_INSTRUCTIONS["AC"])
    prompt = f"""
You previously generated this {code_type} code for the problem below.
It has a bug. Fix it.

Original instruction:
{instruction}

Problem:
{problem.model_dump_json(indent=2)}

Language: {language}

Broken code:
```{language}
{broken_code}
```

Error / wrong-answer feedback:
{error_feedback}

Return ONLY the fixed complete source code. Do not use markdown fences.
"""
    return _strip_code_fence(request_text([{"role": "user", "content": prompt}]))


def generate_input_generator(problem: ProblemSchema, profile: str = "SMALL") -> str:
    prompt = f"""
{TYPE_INSTRUCTIONS["GENERATOR"]}

Problem:
{problem.model_dump_json(indent=2)}

Profile: {profile}

Rules:
- Use only Python standard library.
- Read the seed with sys.stdin.read(); if it is blank, use seed 0.
- Print exactly one complete stdin input for the problem, not multiple labelled cases.
- Keep the full output under 5000 characters for SMALL stress rounds.
- If the statement has a leading t/T, print a valid t and exactly that many scenarios.
- Do not print explanations, markdown, expected output, or comments outside the source.

Return ONLY the complete Python source code.
"""
    return _strip_code_fence(request_text([{"role": "user", "content": prompt}]))


def _find_text_value(data: dict, *keys: str) -> str:
    for key in keys:
        value = data.get(key)
        text = _coerce_text(value)
        if text.strip():
            return text
    return ""


def _coerce_text(value) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        for key in ("code", "source_code", "solution", "content", "text"):
            text = _coerce_text(value.get(key))
            if text.strip():
                return text
    return ""


def _strip_code_fence(content: str) -> str:
    text = content.strip()
    if not text.startswith("```"):
        return text

    lines = text.splitlines()
    if len(lines) >= 3 and lines[-1].strip() == "```":
        return "\n".join(lines[1:-1]).strip()
    return text.strip("`").strip()


def _validation_cases_prompt(
    validation_cases: list[TestCaseSchema], code_type: str
) -> str:
    if not validation_cases:
        return ""

    examples: list[str] = []
    for index, case in enumerate(validation_cases[:8], start=1):
        input_text = (case.input or "").strip()
        expected = (case.expected_output or "").strip()
        if not input_text or not expected:
            continue
        if len(input_text) > 4000 or len(expected) > 4000:
            continue
        examples.append(
            f"Validation case #{index}\n"
            f"Input:\n{input_text}\n"
            f"Expected output:\n{expected}"
        )

    if not examples:
        return ""

    if code_type == "TLE":
        rule = (
            "For the validation cases below, your TLE solution must either print "
            "exactly the expected output or run too slowly on genuinely large "
            "inputs. It must never finish with a different output."
        )
    else:
        rule = "Use the validation cases below to match the expected output style."

    return rule + "\n\n" + "\n\n".join(examples)
