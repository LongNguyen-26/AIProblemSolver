from typing import List, Optional

from pydantic import BaseModel, Field


class ProblemSchema(BaseModel):
    title: str = ""
    description: str = ""
    input_format: str = ""
    output_format: str = ""
    constraints: List[str] = Field(default_factory=list)
    sample_inputs: List[str] = Field(default_factory=list)
    sample_outputs: List[str] = Field(default_factory=list)


class TestCaseSchema(BaseModel):
    id: str
    input: str
    expected_output: str
    description: str = ""
    is_edge_case: bool = False


class AnalyzeRequest(BaseModel):
    text: Optional[str] = None
    image_base64: Optional[str] = None


class AnalyzeResponse(BaseModel):
    problem: ProblemSchema


class ComplexityRequest(BaseModel):
    problem: ProblemSchema


class ComplexityInfo(BaseModel):
    optimal_complexity: str = "unknown"
    tle_target_complexity: str = "O(n^2)"
    max_n: int = 0
    tle_strategy: str = "input_dependent_bruteforce"
    tle_explanation: str = (
        "Use a straightforward but correct algorithm whose work grows with input size."
    )


class TestCaseRequest(BaseModel):
    problem: ProblemSchema
    count: int = 10
    requested_count: Optional[int] = None
    include_edge_cases: bool = True
    profile: str = "SMALL"
    existing_inputs: List[str] = Field(default_factory=list)


class TestCaseResponse(BaseModel):
    testcases: List[TestCaseSchema]
    checker_code: str = ""


class StressRequest(BaseModel):
    problem: ProblemSchema
    small_cases: List[TestCaseSchema] = Field(default_factory=list)
    rounds: int = 30


class StressResult(BaseModel):
    trusted: bool = False
    trusted_oracle_code: str = ""
    trusted_oracle_language: str = "python"
    found_counterexample: bool = False
    counterexample_input: str = ""
    brute_output: str = ""
    optimized_output: str = ""
    rounds_completed: int = 0
    oracle_retries: int = 0
    generator_trusted: bool = False
    message: str = ""
    problem_type: str = "general"


class CodeGenRequest(BaseModel):
    problem: ProblemSchema
    type: str = "AC"
    language: str = "cpp"
    validation_cases: List[TestCaseSchema] = Field(default_factory=list)
    error_log: str = ""
    complexity_info: dict = Field(default_factory=dict)


class CodeGenResponse(BaseModel):
    code: str
    language: str
    type: str
    explanation: str = ""
