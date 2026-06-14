import shutil
import uuid
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, File, Form, UploadFile

from app.container import Container, get_container
from app.schemas import (
    ApiResponse,
    EvaluationRequest,
    KnowledgeSearchRequest,
    PlanRequest,
    TutorRequest,
)

app = FastAPI(title="AI Learning Service", version="0.1.0")


@app.get("/health")
async def health(container: Annotated[Container, Depends(get_container)]):
    return {
        "status": "UP",
        "llm_provider": container.settings.llm_provider,
        "rag_chunks": len(container.rag.chunks),
    }


@app.post("/ai/v1/plans/generate", response_model=ApiResponse)
async def generate_plan(
    request: PlanRequest, container: Annotated[Container, Depends(get_container)]
):
    result = await container.learning_graph.generate_plan(
        {
            **request.model_dump(exclude_none=True),
            "user_profile": request.user_profile.model_dump() if request.user_profile else None,
            "trace": [],
        }
    )
    return ApiResponse(
        request_id=request.request_id,
        data={
            "learning_plan": result["learning_plan"],
            "resources": result["resources"],
            "content": result["content"],
            "quizzes": result["quizzes"],
        },
        trace=result["trace"],
    )


@app.post("/ai/v1/evaluations/answer", response_model=ApiResponse)
async def evaluate(
    request: EvaluationRequest, container: Annotated[Container, Depends(get_container)]
):
    result = await container.learning_graph.evaluate(
        {
            **request.model_dump(exclude_none=True),
            "answers": [item.model_dump() for item in request.answers],
            "user_profile": request.user_profile.model_dump() if request.user_profile else None,
            "trace": [],
        }
    )
    return ApiResponse(
        request_id=request.request_id,
        data={
            "evaluation": result["evaluation"],
            "profile_update": result["profile_update"],
            "next_plan": result.get("learning_plan"),
            "resources": result.get("resources", []),
        },
        trace=result["trace"],
    )


@app.post("/ai/v1/tutor/chat", response_model=ApiResponse)
async def tutor(
    request: TutorRequest, container: Annotated[Container, Depends(get_container)]
):
    result = await container.tutor_graph.chat(
        {
            **request.model_dump(exclude_none=True),
            "user_profile": request.user_profile.model_dump() if request.user_profile else None,
            "trace": [],
        }
    )
    return ApiResponse(
        request_id=request.request_id,
        data={
            "answer": result["answer"],
            "intent": result["intent"],
            "citations": [item["chunk_id"] for item in result.get("contexts", [])],
        },
        trace=result["trace"],
    )


@app.post("/ai/v1/knowledge/search", response_model=ApiResponse)
async def search(
    request: KnowledgeSearchRequest,
    container: Annotated[Container, Depends(get_container)],
):
    results = container.rag.search(request.query, request.top_k, request.document_id)
    return ApiResponse(request_id=str(uuid.uuid4()), data={"chunks": results})


@app.post("/ai/v1/knowledge/documents", response_model=ApiResponse)
async def upload_document(
    file: Annotated[UploadFile, File()],
    title: Annotated[str, Form()],
    container: Annotated[Container, Depends(get_container)],
):
    suffix = Path(file.filename or "").suffix.lower()
    target = container.settings.data_dir / f"upload-{uuid.uuid4()}{suffix}"
    with target.open("wb") as output:
        shutil.copyfileobj(file.file, output)
    try:
        result = container.rag.ingest(target, title)
    finally:
        target.unlink(missing_ok=True)
    return ApiResponse(request_id=str(uuid.uuid4()), data=result)

