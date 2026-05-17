from fastapi import APIRouter

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
        return StressResult(
            trusted=False,
            trusted_oracle_language="python",
            rounds_completed=0,
            generator_trusted=False,
            message=f"Stress agent gap loi: {exc}. Dung fallback oracle neu co.",
            problem_type="general",
        )
