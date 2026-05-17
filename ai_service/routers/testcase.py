from fastapi import APIRouter, HTTPException

from models.schemas import TestCaseRequest, TestCaseResponse
from services.testcase_generator import generate_testcases

router = APIRouter()


@router.post("/testcase", response_model=TestCaseResponse)
async def testcase(request: TestCaseRequest):
    try:
        requested_count = request.requested_count or request.count
        return generate_testcases(
            request.problem,
            requested_count,
            request.include_edge_cases,
            request.profile,
            request.existing_inputs,
        )
    except Exception as exc:
        raise HTTPException(500, str(exc)) from exc
