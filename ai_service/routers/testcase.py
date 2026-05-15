from fastapi import APIRouter, HTTPException

from models.schemas import TestCaseRequest, TestCaseResponse
from services.testcase_generator import generate_testcases

router = APIRouter()


@router.post("/testcase", response_model=TestCaseResponse)
async def testcase(request: TestCaseRequest):
    try:
        return generate_testcases(
            request.problem, request.count, request.include_edge_cases, request.profile
        )
    except Exception as exc:
        raise HTTPException(500, str(exc)) from exc
