import json

from openai import OpenAI as GroqClient

from config import GROQ_BASE_URL, GROQ_MODEL, MAX_TOKENS, require_groq_api_key
from models.schemas import CodeGenResponse, ProblemSchema


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
"""

    client = GroqClient(api_key=require_groq_api_key(), base_url=GROQ_BASE_URL)
    response = client.chat.completions.create(
        model=GROQ_MODEL,
        max_tokens=MAX_TOKENS,
        response_format={"type": "json_object"},
        messages=[{"role": "user", "content": prompt}],
    )

    content = response.choices[0].message.content or "{}"
    data = json.loads(content)
    return CodeGenResponse(
        code=data.get("code", ""),
        language=language,
        type=code_type,
        explanation=data.get("explanation", ""),
    )
