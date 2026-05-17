from fastapi import APIRouter, HTTPException

from models.schemas import StressRequest, StressResult
from services.stress_testing_agent import StressTestingAgent

router = APIRouter()


@router.post("/stress", response_model=StressResult)
async def stress(request: StressRequest):
    try:
        return StressTestingAgent().run(
            problem=request.problem,
            small_cases=request.small_cases,
            rounds=request.rounds,
        )
    except Exception as exc:
        raise HTTPException(500, str(exc)) from exc
