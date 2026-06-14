import asyncio
from pathlib import Path

from app.config import Settings
from app.llm.provider import MockLLMProvider
from app.memory import ProfileClient
from app.rag import RagService
from app.tools import ToolRegistry


def test_evaluate_answer(tmp_path: Path):
    settings = Settings(data_dir=tmp_path)
    tools = ToolRegistry(MockLLMProvider(), RagService(settings), ProfileClient(settings))
    quizzes = [
        {
            "id": "q1",
            "type": "single_choice",
            "answer": "A",
            "explanation": "ok",
        }
    ]
    result = asyncio.run(
        tools.evaluate_answer("Java", quizzes, [{"quiz_id": "q1", "answer": "A"}])
    )
    assert result["score"] == 100
    assert result["passed"] is True

