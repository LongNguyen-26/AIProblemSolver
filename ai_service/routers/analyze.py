from fastapi import APIRouter, HTTPException

from models.schemas import AnalyzeRequest, AnalyzeResponse
from services.ocr_service import image_base64_to_text
from services.problem_analyzer import analyze_problem

router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest):
    if not request.text and not request.image_base64:
        raise HTTPException(400, "Provide either 'text' or 'image_base64'")

    text = request.text or ""
    if request.image_base64:
        try:
            image_text = image_base64_to_text(request.image_base64)
        except RuntimeError as exc:
            raise HTTPException(500, str(exc)) from exc
        text = f"{text}\n{image_text}".strip()

    try:
        problem = analyze_problem(text)
    except Exception as exc:
        raise HTTPException(500, str(exc)) from exc
    return AnalyzeResponse(problem=problem)
