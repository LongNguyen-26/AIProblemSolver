from fastapi import APIRouter

from models.schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    ComplexityInfo,
    ComplexityRequest,
    ProblemSchema,
)
from services.complexity_analyzer import analyze_complexity
from services.ocr_service import image_base64_to_text
from services.problem_analyzer import analyze_problem, fallback_analyze_problem

router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest):
    if not request.text and not request.image_base64:
        return AnalyzeResponse(problem=_fallback_problem("", "No input was provided."))

    text = request.text or ""
    if request.image_base64:
        try:
            image_text = image_base64_to_text(request.image_base64)
        except RuntimeError as exc:
            return AnalyzeResponse(problem=_fallback_problem(text, f"OCR failed: {exc}"))
        text = f"{text}\n{image_text}".strip()

    try:
        problem = analyze_problem(text)
    except Exception as exc:
        return AnalyzeResponse(problem=_fallback_problem(text, f"Analyze failed: {exc}"))
    return AnalyzeResponse(problem=problem)


@router.post("/analyze/complexity", response_model=ComplexityInfo)
async def complexity(request: ComplexityRequest):
    try:
        return analyze_complexity(request.problem)
    except Exception:
        return ComplexityInfo()


def _fallback_problem(text: str, reason: str) -> ProblemSchema:
    return fallback_analyze_problem(text, reason)
