from fastapi import APIRouter

from models.schemas import CodeGenRequest, CodeGenResponse, WaRetryRequest
from services.code_generator import generate_code

router = APIRouter()


@router.post("/codegen", response_model=CodeGenResponse)
async def codegen(request: CodeGenRequest):
    try:
        return generate_code(
            request.problem,
            request.type,
            request.language,
            request.validation_cases,
            request.complexity_info,
            request.error_log,
        )
    except Exception as exc:
        return CodeGenResponse(
            code="",
            language=request.language,
            type=request.type,
            explanation=f"Sinh code that bai, thu lai: {exc}",
        )


@router.post("/codegen/wa_retry", response_model=CodeGenResponse)
async def wa_retry(request: WaRetryRequest):
    try:
        return generate_code(
            request.problem,
            "WA",
            request.language,
            request.sample_cases,
            None,
            request.feedback,
        )
    except Exception as exc:
        return CodeGenResponse(
            code="",
            language=request.language,
            type="WA",
            explanation=f"Sinh code WA that bai, thu lai: {exc}",
        )
