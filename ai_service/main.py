from fastapi import FastAPI

from routers import analyze, codegen, pipeline, stress, testcase

app = FastAPI(title="AIProblemSolver AI Service", version="1.0.0")

app.include_router(analyze.router)
app.include_router(testcase.router)
app.include_router(codegen.router)
app.include_router(stress.router)
app.include_router(pipeline.router)


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
