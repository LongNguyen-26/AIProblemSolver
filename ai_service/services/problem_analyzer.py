import json

from openai import OpenAI as GroqClient

from config import GROQ_BASE_URL, GROQ_MODEL, MAX_TOKENS, require_groq_api_key
from models.schemas import ProblemSchema


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
"""


def analyze_problem(text: str) -> ProblemSchema:
    client = GroqClient(api_key=require_groq_api_key(), base_url=GROQ_BASE_URL)
    response = client.chat.completions.create(
        model=GROQ_MODEL,
        max_tokens=MAX_TOKENS,
        response_format={"type": "json_object"},
        messages=[
            {"role": "system", "content": ANALYZE_SYSTEM_PROMPT},
            {"role": "user", "content": f"Analyze this problem:\n\n{text}"},
        ],
    )
    content = response.choices[0].message.content or "{}"
    data = json.loads(content)
    return ProblemSchema(**data)
