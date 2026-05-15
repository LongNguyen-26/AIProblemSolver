from models.schemas import CodeGenResponse, ProblemSchema
from services.groq_json import request_json, request_text


TYPE_INSTRUCTIONS = {
    "AC": "Write a CORRECT, optimal solution. It must pass all test cases.",
    "WA": (
        "Write a solution with a subtle bug that gives WRONG ANSWER on some "
        "cases. The bug should not be obvious."
    ),
    "TLE": (
        "Write a solution that is CORRECT but has TIME LIMIT EXCEEDED due to "
        "poor complexity (e.g., O(n^2) when O(n) is needed)."
    ),
}


def generate_code(
    problem: ProblemSchema, code_type: str, language: str
) -> CodeGenResponse:
    instruction = TYPE_INSTRUCTIONS.get(code_type, TYPE_INSTRUCTIONS["AC"])
    prompt = f"""
{instruction}

Problem:
{problem.model_dump_json(indent=2)}

Language: {language}

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
        code = _generate_code_text(problem, code_type, language, instruction)
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
