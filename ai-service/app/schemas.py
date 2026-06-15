import uuid
from typing import Any, Literal

from pydantic import AliasChoices, BaseModel, Field


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
    request_id: str = Field(
        default_factory=lambda: str(uuid.uuid4()),
        validation_alias=AliasChoices("request_id", "requestId"),
    )
    user_id: int = Field(validation_alias=AliasChoices("user_id", "userId"))
    task_id: int | None = Field(
        default=None,
        validation_alias=AliasChoices("task_id", "taskId"),
    )
    message: str
    user_profile: UserProfile | None = Field(
        default=None,
        validation_alias=AliasChoices("user_profile", "userProfile"),
    )


class KnowledgeSearchRequest(BaseModel):
    query: str
    top_k: int = 5
    document_id: str | None = None


class SpeechRequest(BaseModel):
    text: str = Field(min_length=1, max_length=6000)


class ApiResponse(BaseModel):
    request_id: str
    success: bool = True
    data: dict[str, Any]
    trace: list[dict[str, Any]] = Field(default_factory=list)


QuestionType = Literal["single_choice", "true_false", "short_answer"]

