from models.schemas import ProblemSchema
from services.groq_json import request_json


ANALYZE_SYSTEM_PROMPT = """
You are an expert competitive programming problem analyzer.
Given a problem statement, extract and return ONLY a JSON object with these fields:
- title: string
- description: string (full problem description)
- input_format: string
- output_format: string
- constraints: array of strings (each constraint as one element)
- sample_inputs: array of strings
- sample_outputs: array of strings

Respond with ONLY valid JSON, no markdown, no explanation.
Escape line breaks inside JSON strings as \\n.
"""


def analyze_problem(text: str) -> ProblemSchema:
    data = request_json(
        [
            {"role": "system", "content": ANALYZE_SYSTEM_PROMPT},
            {"role": "user", "content": f"Analyze this problem:\n\n{text}"},
        ]
    )
    return ProblemSchema(**data)
