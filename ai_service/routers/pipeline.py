import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from models.schemas import PipelineProgress, PipelineRequest
from services.pipeline_orchestrator import PipelineOrchestrator

router = APIRouter()


@router.post("/pipeline/run")
async def run_pipeline(request: PipelineRequest):
    async def event_stream():
        orchestrator = PipelineOrchestrator(request)
        try:
            async for progress in orchestrator.run():
                yield _sse(progress)
        except Exception as exc:
            cases = orchestrator._all_cases()
            yield _sse(
                PipelineProgress(
                    state="DONE",
                    message=f"Pipeline gap loi: {exc}. Tra ket qua tam thoi.",
                    progress_pct=100,
                    testcases_ready=len(cases),
                    testcases_target=max(1, min(request.count or 10, 50)),
                    warnings=[f"Pipeline gap loi: {exc}"],
                    problem=orchestrator.problem,
                    all_testcases=cases,
                    cached=False,
                )
            )

    return StreamingResponse(event_stream(), media_type="text/event-stream")


def _sse(progress: PipelineProgress) -> str:
    payload = progress.model_dump()
    return "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"
