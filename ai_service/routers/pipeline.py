import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from models.schemas import PipelineRequest
from services.pipeline_orchestrator import PipelineOrchestrator

router = APIRouter()


@router.post("/pipeline/run")
async def run_pipeline(request: PipelineRequest):
    async def event_stream():
        orchestrator = PipelineOrchestrator(request)
        async for progress in orchestrator.run():
            payload = progress.model_dump()
            yield "data: " + json.dumps(payload, ensure_ascii=False) + "\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
