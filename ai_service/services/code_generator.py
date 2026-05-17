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
        "Write a solution with a subtle bug that gives WRONG ANSWER on some "
        "cases. The bug should not be obvious."
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


def generate_code(
    problem: ProblemSchema,
    code_type: str,
    language: str,
    validation_cases: list[TestCaseSchema] | None = None,
    complexity_info: dict[str, Any] | ComplexityInfo | None = None,
    error_log: str = "",
) -> CodeGenResponse:
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
