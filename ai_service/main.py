import traceback

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from routers import analyze, codegen, pipeline, stress, testcase

app = FastAPI(title="AIProblemSolver AI Service", version="1.0.0")

app.include_router(analyze.router)
app.include_router(testcase.router)
app.include_router(codegen.router)
app.include_router(stress.router)
app.include_router(pipeline.router)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={
            "error": str(exc),
            "detail": traceback.format_exc()[-500:],
            "fallback": True,
        },
    )


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
