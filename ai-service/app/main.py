import logging
import json
import shutil
import uuid
from pathlib import Path
from typing import Annotated

import httpx
from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse, Response

from app.container import Container, get_container
from app.schemas import (
    ApiResponse,
    EvaluationRequest,
    KnowledgeSearchRequest,
    PlanRequest,
    TutorRequest,
)

app = FastAPI(title="AI Learning Service", version="0.1.0")
logger = logging.getLogger("uvicorn.error")


@app.exception_handler(RequestValidationError)
async def validation_error(request: Request, exception: RequestValidationError):
    errors = exception.errors()
    logger.warning(
        "Request validation failed: method=%s path=%s client=%s content_length=%s errors=%s",
        request.method,
        request.url.path,
        request.client.host if request.client else "unknown",
        request.headers.get("content-length"),
        errors,
    )
    return JSONResponse(
        status_code=422,
        content=jsonable_encoder({
            "success": False,
            "message": "请求参数校验失败",
            "detail": errors,
        }),
    )


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


@app.post("/ai/v1/tts")
async def synthesize_speech(
    request: Request, container: Annotated[Container, Depends(get_container)]
):
    body = await request.body()
    content_type = request.headers.get("content-type")
    if not body:
        logger.warning(
            "TTS request body is empty: client=%s content_type=%s",
            request.client.host if request.client else "unknown",
            content_type,
        )
        raise HTTPException(status_code=422, detail="语音文本不能为空")
    try:
        payload = json.loads(body.decode("utf-8"))
        if isinstance(payload, str):
            payload = json.loads(payload)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        logger.warning(
            "TTS request body is not valid JSON: client=%s content_type=%s length=%s preview=%r",
            request.client.host if request.client else "unknown",
            content_type,
            len(body),
            body[:120],
        )
        raise HTTPException(status_code=422, detail="语音请求必须是 JSON") from exception

    text = payload.get("text") if isinstance(payload, dict) else None
    if not isinstance(text, str) or not text.strip():
        logger.warning(
            "TTS request missing text: client=%s content_type=%s payload_type=%s",
            request.client.host if request.client else "unknown",
            content_type,
            type(payload).__name__,
        )
        raise HTTPException(status_code=422, detail="语音文本不能为空")
    text = text.strip()[:6000]
    try:
        audio = await container.tts.synthesize(text)
        return Response(
            content=audio,
            media_type="audio/wav",
            headers={"Cache-Control": "private, max-age=3600"},
        )
    except RuntimeError as exception:
        raise HTTPException(status_code=503, detail=str(exception)) from exception
    except httpx.HTTPStatusError as exception:
        logger.error(
            "Gemini TTS failed: status=%s body=%s",
            exception.response.status_code,
            exception.response.text,
        )
        raise HTTPException(status_code=502, detail="Gemini 语音生成失败") from exception
    except httpx.HTTPError as exception:
        logger.error("Gemini TTS request failed: %s", exception)
        raise HTTPException(status_code=502, detail="Gemini 语音服务连接失败") from exception


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

