from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_tutor_chat_accepts_backend_payload():
    response = client.post(
        "/ai/v1/tutor/chat",
        json={
            "request_id": "test-request",
            "user_id": 1,
            "task_id": 0,
            "message": "Java 是什么？",
            "user_profile": {
                "level": "beginner",
                "mastery": {},
                "weak_points": [],
                "preferences": [],
                "learning_history": [],
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["success"] is True
    assert body["data"]["answer"]


def test_tutor_chat_returns_readable_validation_error():
    response = client.post("/ai/v1/tutor/chat", json={"message": "测试"})

    assert response.status_code == 422
    body = response.json()
    assert body["success"] is False
    assert body["message"] == "请求参数校验失败"
    assert body["detail"]


def test_tutor_chat_accepts_camel_case_payload():
    response = client.post(
        "/ai/v1/tutor/chat",
        json={
            "userId": 1,
            "taskId": None,
            "message": "帮我制定学习计划",
            "userProfile": {
                "level": "beginner",
                "mastery": {},
                "weak_points": [],
                "preferences": [],
                "learning_history": [],
            },
        },
    )

    assert response.status_code == 200
