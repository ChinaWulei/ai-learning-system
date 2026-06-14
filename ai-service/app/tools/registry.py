import time
import uuid
from collections.abc import Awaitable, Callable
from typing import Any

from app.llm import LLMProvider
from app.memory import ProfileClient
from app.rag import RagService

ToolFunction = Callable[..., Awaitable[Any]]


class ToolRegistry:
    def __init__(self, llm: LLMProvider, rag: RagService, profiles: ProfileClient):
        self.llm = llm
        self.rag = rag
        self.profiles = profiles
        self.tools: dict[str, ToolFunction] = {
            "search_knowledge_base": self.search_knowledge_base,
            "generate_learning_plan": self.generate_learning_plan,
            "generate_learning_content": self.generate_learning_content,
            "generate_quiz": self.generate_quiz,
            "evaluate_answer": self.evaluate_answer,
            "retrieve_user_profile": self.retrieve_user_profile,
            "answer_tutor_question": self.answer_tutor_question,
        }

    async def invoke(self, name: str, trace: list[dict[str, Any]], **kwargs: Any) -> Any:
        if name not in self.tools:
            raise KeyError(f"Unknown tool: {name}")
        started = time.perf_counter()
        result = await self.tools[name](**kwargs)
        trace.append(
            {
                "type": "tool",
                "name": name,
                "duration_ms": round((time.perf_counter() - started) * 1000, 2),
            }
        )
        return result

    async def search_knowledge_base(
        self, query: str, top_k: int = 5, document_id: str | None = None
    ) -> list[dict[str, Any]]:
        return self.rag.search(query, top_k, document_id)

    async def retrieve_user_profile(
        self, user_id: int, supplied: dict[str, Any] | None = None
    ) -> dict[str, Any]:
        return await self.profiles.get(user_id, supplied)

    async def generate_learning_plan(
        self, topic: str, goal: str, profile: dict[str, Any], resources: list[dict[str, Any]]
    ) -> dict[str, Any]:
        default = {
            "topic": topic,
            "goal": goal or f"掌握{topic}的核心知识并能完成基础应用",
            "estimated_hours": 6,
            "stages": [
                {"order": 1, "name": "概念建立", "objective": f"理解{topic}核心概念", "hours": 2},
                {"order": 2, "name": "案例实践", "objective": f"完成{topic}典型案例", "hours": 2},
                {"order": 3, "name": "练习评估", "objective": "通过练习定位薄弱点", "hours": 2},
            ],
        }
        return await self.llm.generate_json(
            "你是学习路径规划工具。输出可执行、循序渐进的结构化计划。",
            f"主题：{topic}\n目标：{goal}\n画像：{profile}\n可用资料：{resources[:3]}",
            default,
        )

    async def generate_quiz(
        self, topic: str, content: str, difficulty: str = "medium", count: int = 3
    ) -> list[dict[str, Any]]:
        defaults = [
            {
                "id": str(uuid.uuid4()),
                "type": "single_choice",
                "knowledge_point": topic,
                "difficulty": difficulty,
                "question": f"关于{topic}，下列哪项描述最准确？",
                "options": ["核心概念需要结合上下文理解", "无需练习", "只需记忆术语", "与实践无关"],
                "answer": "A",
                "explanation": "学习应结合概念、上下文和实践。",
            },
            {
                "id": str(uuid.uuid4()),
                "type": "true_false",
                "knowledge_point": topic,
                "difficulty": difficulty,
                "question": f"学习{topic}时，练习反馈有助于发现薄弱点。",
                "options": ["正确", "错误"],
                "answer": "正确",
                "explanation": "反馈是形成学习闭环的重要环节。",
            },
            {
                "id": str(uuid.uuid4()),
                "type": "short_answer",
                "knowledge_point": topic,
                "difficulty": difficulty,
                "question": f"请用自己的话概括{topic}的核心思想。",
                "options": [],
                "answer": f"能够说明{topic}的关键概念、作用与一个应用场景。",
                "explanation": "按概念、作用和应用三个维度评分。",
            },
        ][:count]
        data = await self.llm.generate_json(
            "你是题目生成工具。题目必须可判分，并包含答案和解析。",
            f"主题：{topic}\n难度：{difficulty}\n学习内容：{content[:1500]}",
            {"quizzes": defaults},
        )
        return data.get("quizzes", defaults)

    async def generate_learning_content(
        self, topic: str, contexts: list[dict[str, Any]]
    ) -> dict[str, Any]:
        context_text = "\n".join(item["content"] for item in contexts)
        if context_text:
            body = await self.llm.chat(
                "你是教学内容生成工具。严格依据资料讲解，并避免引入无依据事实。",
                f"主题：{topic}\n资料：{context_text[:4000]}",
            )
        else:
            body = f"{topic}：先建立核心概念，再通过案例和练习形成理解。当前知识库无相关资料，此内容为通用模型生成。"
        return {
            "title": f"{topic}核心讲解",
            "body": body,
            "citations": [item["chunk_id"] for item in contexts],
            "grounded": bool(contexts),
        }

    async def answer_tutor_question(
        self, question: str, contexts: list[dict[str, Any]]
    ) -> str:
        context_text = "\n".join(item["content"] for item in contexts)
        return await self.llm.chat(
            "你是对话式学习助手。优先依据检索资料回答；资料不足时明确说明。",
            f"问题：{question}\n资料：{context_text[:4000]}",
        )

    async def evaluate_answer(
        self, topic: str, quizzes: list[dict[str, Any]], answers: list[dict[str, Any]]
    ) -> dict[str, Any]:
        answer_map = {item["quiz_id"]: item["answer"].strip() for item in answers}
        details, total = [], 0.0
        for quiz in quizzes:
            submitted = answer_map.get(quiz["id"], "")
            reference = str(quiz["answer"]).strip()
            if quiz["type"] == "short_answer":
                keywords = [word for word in (topic, "概念", "作用", "应用") if word]
                score = min(100.0, sum(word in submitted for word in keywords) * 25.0)
            else:
                score = 100.0 if submitted.lower() == reference.lower() else 0.0
            total += score
            details.append(
                {
                    "quiz_id": quiz["id"],
                    "score": score,
                    "correct": score >= 60,
                    "submitted": submitted,
                    "reference": reference,
                    "feedback": quiz.get("explanation", ""),
                }
            )
        overall = round(total / max(len(quizzes), 1), 2)
        return {
            "score": overall,
            "mastery": round(overall / 100, 2),
            "passed": overall >= 60,
            "details": details,
            "weak_points": [] if overall >= 85 else [topic],
            "recommendation": "进入下一知识点" if overall >= 85 else "复习相关片段并进行针对性练习",
        }
