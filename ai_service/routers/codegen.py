from fastapi import APIRouter, HTTPException

from models.schemas import CodeGenRequest, CodeGenResponse
from services.code_generator import generate_code

router = APIRouter()


@router.post("/codegen", response_model=CodeGenResponse)
async def codegen(request: CodeGenRequest):
    try:
        return generate_code(request.problem, request.type, request.language)
    except Exception as exc:
        raise HTTPException(500, str(exc)) from exc
