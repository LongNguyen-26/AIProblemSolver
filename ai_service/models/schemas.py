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


class TestCaseRequest(BaseModel):
    problem: ProblemSchema
    count: int = 10
    include_edge_cases: bool = True
    profile: str = "SMALL"


class TestCaseResponse(BaseModel):
    testcases: List[TestCaseSchema]
    checker_code: str = ""


class CodeGenRequest(BaseModel):
    problem: ProblemSchema
    type: str = "AC"
    language: str = "cpp"
    validation_cases: List[TestCaseSchema] = Field(default_factory=list)


class CodeGenResponse(BaseModel):
    code: str
    language: str
    type: str
    explanation: str = ""
