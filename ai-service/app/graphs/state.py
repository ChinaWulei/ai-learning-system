from typing import Any, TypedDict


class LearningState(TypedDict, total=False):
    request_id: str
    user_id: int
    task_id: int
    topic: str
    goal: str
    user_profile: dict[str, Any]
    resources: list[dict[str, Any]]
    learning_plan: dict[str, Any]
    content: list[dict[str, Any]]
    quizzes: list[dict[str, Any]]
    answers: list[dict[str, Any]]
    evaluation: dict[str, Any]
    profile_update: dict[str, Any]
    route: str
    trace: list[dict[str, Any]]


class TutorState(TypedDict, total=False):
    request_id: str
    user_id: int
    task_id: int | None
    message: str
    user_profile: dict[str, Any]
    intent: str
    contexts: list[dict[str, Any]]
    answer: str
    trace: list[dict[str, Any]]

