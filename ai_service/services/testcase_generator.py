import json
import uuid

from openai import OpenAI as GroqClient

from config import GROQ_BASE_URL, GROQ_MODEL, MAX_TOKENS, require_groq_api_key
from models.schemas import ProblemSchema, TestCaseResponse, TestCaseSchema


def generate_testcases(
    problem: ProblemSchema, count: int, include_edge_cases: bool
) -> TestCaseResponse:
    edge_case_text = " including edge cases" if include_edge_cases else ""
    prompt = f"""
You are an expert at generating test cases for competitive programming problems.

Problem:
{problem.model_dump_json(indent=2)}

Generate {count} test cases{edge_case_text}.
Also generate a Python checker function.

Return ONLY valid JSON with this structure:
{{
  "testcases": [
    {{
      "input": "...",
      "expected_output": "...",
      "description": "...",
      "is_edge_case": false
    }}
  ],
  "checker_code": "# Python checker\\ndef check(input_data, expected, actual):\\n    return expected.strip() == actual.strip()"
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
    testcases = [
        TestCaseSchema(id=f"tc_{str(uuid.uuid4())[:8]}", **testcase)
        for testcase in data.get("testcases", [])
    ]
    return TestCaseResponse(
        testcases=testcases,
        checker_code=data.get("checker_code", ""),
    )
