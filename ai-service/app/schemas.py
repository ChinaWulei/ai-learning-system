from typing import Any, Literal

from pydantic import BaseModel, Field


class UserProfile(BaseModel):
    level: str = "beginner"
    mastery: dict[str, float] = Field(default_factory=dict)
    weak_points: list[str] = Field(default_factory=list)
    preferences: list[str] = Field(default_factory=list)
    learning_history: list[dict[str, Any]] = Field(default_factory=list)


class PlanRequest(BaseModel):
    request_id: str
    user_id: int
    task_id: int
    topic: str
    goal: str = ""
    user_profile: UserProfile | None = None


class AnswerItem(BaseModel):
    quiz_id: str
    answer: str


class EvaluationRequest(BaseModel):
    request_id: str
    user_id: int
    task_id: int
    topic: str
    quizzes: list[dict[str, Any]]
    answers: list[AnswerItem]
    user_profile: UserProfile | None = None


class TutorRequest(BaseModel):
    request_id: str
    user_id: int
    task_id: int | None = None
    message: str
    user_profile: UserProfile | None = None


class KnowledgeSearchRequest(BaseModel):
    query: str
    top_k: int = 5
    document_id: str | None = None


class ApiResponse(BaseModel):
    request_id: str
    success: bool = True
    data: dict[str, Any]
    trace: list[dict[str, Any]] = Field(default_factory=list)


QuestionType = Literal["single_choice", "true_false", "short_answer"]

