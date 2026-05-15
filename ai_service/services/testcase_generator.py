import uuid

from models.schemas import ProblemSchema, TestCaseResponse, TestCaseSchema
from services.groq_json import request_json


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
Escape line breaks inside JSON strings as \\n.
"""

    data = request_json([{"role": "user", "content": prompt}])
    testcases = [
        TestCaseSchema(id=f"tc_{str(uuid.uuid4())[:8]}", **testcase)
        for testcase in data.get("testcases", [])
    ]
    return TestCaseResponse(
        testcases=testcases,
        checker_code=data.get("checker_code", ""),
    )
